extern crate grep;

use std::os::raw::c_char;

use grep::regex::RegexMatcher;
use grep::searcher::Searcher;
use walkdir::DirEntry;
use walkdir::WalkDir;

use parse::*;
pub use types::*;

#[no_mangle]
pub extern "C" fn search_file(
    // every Java type is nullable, represented here as an Option<*type>
    filename: *const c_char,
    search_text: *const c_char,
    result_callback: Option<SearchResultCallbackFn>,
) -> SearchStatusCode {
    use SearchStatusCode::*;

    let path = match parse_path(filename) {
        Ok(path) => path,
        Err(code) => return code,
    };

    let matcher: RegexMatcher = match parse_search_text(search_text) {
        Ok(matcher) => matcher,
        Err(code) => return code,
    };

    // the Sink type accepts search results from ripgrep
    let sink = match result_callback {
        Some(callback) => SearchResultCallbackSink(callback, &path),
        None => return MissingCallback,
    };

    match Searcher::new().search_path(&matcher, &path, sink) {
        Ok(_) => return Success,
        Err(_) => return ErrorFromCallback,
    };
}

#[no_mangle]
pub extern "C" fn search_dir(
    // every Java type is nullable, represented here as an Option<*type>
    filename: *const c_char,
    search_text: *const c_char,
    result_callback: Option<SearchResultCallbackFn>,
) -> SearchStatusCode {
    use SearchStatusCode::*;

    let dir = match parse_path(filename) {
        Ok(dir) => dir,
        Err(code) => return code,
    };

    let matcher: RegexMatcher = match parse_search_text(search_text) {
        Ok(matcher) => matcher,
        Err(code) => return code,
    };

    let callback = match result_callback {
        Some(callback) => callback,
        None => return MissingCallback,
    };

    fn is_hidden(entry: &DirEntry) -> bool {
        entry
            .file_name()
            .to_str()
            .map(|s| s.starts_with("."))
            .unwrap_or(false)
    }

    let walker = WalkDir::new(&dir).into_iter();
    for entry in walker.filter_entry(|e| !is_hidden(e)) {
        let entry = match entry {
            Ok(entry) => entry,
            Err(_) => return ErrorCouldNotOpenFile,
        };

        if !entry.file_type().is_file() {
            continue;
        }

        // Pass cloned sink from the outer scope.
        // This is probably fine, since we're just cloning a function pointer.
        // We'll trust our wrapper class to handle being called by multiple threads at once.
        if Searcher::new()
            .search_path(
                &matcher,
                entry.path(),
                SearchResultCallbackSink(callback, entry.path()),
            )
            .is_err()
        {
            return ErrorFromCallback;
        };
    }
    return Success;
}

// Defines the various types and enums used by this wrapper library
mod types {
    use std::fmt;
    use std::os::raw::c_char;
    use std::os::raw::c_int;
    use std::path::Path;

    use grep::searcher::{Searcher, Sink, SinkError, SinkMatch};

    // For use returning back through the FFI.
    // Note that the bytes inside are NOT nul-terminated!
    #[repr(C)]
    #[no_mangle] // or else JNA can't determine what fields the struct has
    pub struct SearchResult {
        pub file_name: *const c_char,
        pub line_number: c_int,
        pub bytes: *const u8,
        // NOT nul-terminated!
        pub num_bytes: c_int,
    }

    #[repr(C)]
    #[derive(Debug, Eq, PartialEq, Clone, Copy)]
    pub enum SearchStatusCode {
        Success = 0,
        // Equivalents to IllegalArgumentExceptions:
        MissingFilename = 1,
        MissingSearchText = 2,
        MissingCallback = 3,
        // Failure from inside ripgrep:
        ErrorBadPattern = 11,
        ErrorCouldNotOpenFile = 12,
        ErrorFromRipgrep = 13,
        // Failure from inside the callback:
        ErrorFromCallback = 21,
    }

    // indicates Success on true, Failure on false
    // #[cfg(not(windows))]
    pub type SearchResultCallbackFn = extern "C" fn(SearchResult) -> bool;
    // #[cfg(windows)]
    // pub type SearchResultCallbackFn = extern "stdcall" fn(SearchResult) -> bool;

    #[derive(Clone)]
    pub struct SearchResultCallbackSink<'a>(pub SearchResultCallbackFn, pub &'a Path);

    pub struct CallbackError {
        error_message: String,
    }

    impl SinkError for CallbackError {
        fn error_message<T: fmt::Display>(message: T) -> Self {
            Self {
                error_message: format!("{}", message),
            }
        }
    }

    impl Sink for SearchResultCallbackSink<'_> {
        type Error = CallbackError;

        fn matched(
            &mut self,
            _searcher: &Searcher,
            matched: &SinkMatch,
        ) -> Result<bool, CallbackError> {
            let result = SearchResult {
                file_name: self.1.to_str().unwrap_or("<unknown file>").as_ptr() as *const i8,
                // -1 is a common value to use in Java when an int value is not found
                line_number: matched.line_number().map(|n| n as c_int).unwrap_or(-1),
                // lifetime should be good because the callback will finish before the buffer is modified.
                // callbacks just need to avoid SAVING the byte array passed to it, and should copy from it instead
                // This is easier than allocating a CString and passing it with a nul-terminator,
                // because this way we don't have to free() anything with another FFI call.
                // The drawback is a bit more work on the Java side using this data,
                // and the risk of retaining a dangling pointer to this buffer.
                bytes: matched.bytes().as_ptr(),
                num_bytes: matched.bytes().len() as c_int,
            };

            let succeeded: bool = (self.0)(result);
            if succeeded {
                Ok(true) // callback done, keep searching
            } else {
                Err(CallbackError::error_message(
                    "Callback completed but indicated an error",
                ))
            }
        }
    }
}

// Handles parsing parameters passed to the library
mod parse {
    use std::ffi::CStr;
    use std::os::raw::c_char;
    use std::path::PathBuf;
    use std::str::{from_utf8, Utf8Error};

    use grep::regex::RegexMatcher;

    use crate::types::*;

    /// Convert a native string to a Rust string
    fn to_string(pointer: *const c_char) -> Result<String, Utf8Error> {
        let cstr = unsafe { CStr::from_ptr(pointer) };
        let slice = cstr.to_bytes();
        from_utf8(slice).map(|s| s.to_string())
    }

    // Either finds the Path with the given name, or returns an error code to pass out of the library
    pub fn parse_path(filename: *const c_char) -> Result<PathBuf, SearchStatusCode> {
        use SearchStatusCode::*;

        // Java owns the string, so we view the text as a &CStr reference rather than an owned CString
        if filename.is_null() {
            return Err(MissingFilename);
        }

        let path = match to_string(filename) {
            Ok(filename) => PathBuf::from(filename),
            Err(_) => return Err(ErrorCouldNotOpenFile),
        };

        if path.exists() {
            Ok(path)
        } else {
            Err(ErrorCouldNotOpenFile)
        }
    }

    // Either generates a regular-expression matcher from the given C-style string,
    // or returns an error code to pass out of the library
    pub fn parse_search_text(search_text: *const c_char) -> Result<RegexMatcher, SearchStatusCode> {
        use SearchStatusCode::*;

        // Java owns the string, so we view the text as a &CStr reference rather than an owned CString
        if search_text.is_null() {
            return Err(MissingSearchText);
        }

        let search_text: String = match to_string(search_text) {
            Ok(search_text) => search_text,
            Err(_) => return Err(ErrorBadPattern),
        };

        match RegexMatcher::new(&search_text) {
            Ok(regex) => Ok(regex),
            Err(_) => return Err(ErrorBadPattern),
        }
    }

    #[cfg(test)]
    mod tests {
        use std::ffi::CString;
        use std::ptr;

        use super::*;

        const BEE_MOVIE_FILE_NAME: &'static str = "src/test/resources/bee_movie.txt";

        #[test]
        fn test_opening_bee_movie_script() {
            let filename = CString::new(BEE_MOVIE_FILE_NAME).unwrap();
            let file = parse_path(filename.as_ptr());
            assert!(
                file.is_ok(),
                "Could not open test resource \"bee_movie.txt\" using a C-style pointer"
            );
        }

        #[test]
        fn test_opening_nonexistant_file_returns_appropriate_error_code() {
            let filename = CString::new("non_existant_file.txt").unwrap();
            assert_eq!(
                SearchStatusCode::ErrorCouldNotOpenFile,
                parse_path(filename.as_ptr())
                    .expect_err("Should not have been able to open missing file")
            );
        }

        #[test]
        fn test_opening_null_filename_returns_appropriate_error_code() {
            assert_eq!(
                SearchStatusCode::MissingFilename,
                parse_path(ptr::null())
                    .expect_err("Should not have been able to open a file with a null filename")
            );
        }
        #[test]
        fn test_parsing_bee_regex() {
            let search_text = CString::new("[Bb]ee").unwrap();
            let file = parse_search_text(search_text.as_ptr());
            assert!(
                file.is_ok(),
                "Could not parse search text \"[Bb]ee\" using a C-style pointer"
            );
        }

        #[test]
        fn test_opening_null_search_text_returns_appropriate_error_code() {
            assert_eq!(
                SearchStatusCode::MissingSearchText,
                parse_search_text(ptr::null()).expect_err(
                    "Should not have been able to parse a search regex from a null string"
                )
            );
        }
    }
}

#[cfg(test)]
mod tests;
