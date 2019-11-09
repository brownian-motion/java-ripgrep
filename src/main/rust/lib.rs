extern crate grep;
use grep::regex::RegexMatcher;
use grep::searcher::Searcher;

use std::fs::File;
use std::os::raw::c_char;

use parse::*;
use types::*;

#[no_mangle]
pub extern "C" fn search_file(
    // every Java type is nullable, represented here as an Option<*type>
    filename: Option<*const c_char>,
    search_text: Option<*const c_char>,
    result_callback: Option<SearchResultCallbackFn>,
) -> SearchStatusCode {
    use SearchStatusCode::*;

    let file: File = match open_filename(filename) {
        Ok(filename) => filename,
        Err(code) => return code,
    };

    let matcher: RegexMatcher = match parse_search_text(search_text) {
        Ok(matcher) => matcher,
        Err(code) => return code,
    };

    // the Sink type accepts search results from ripgrep
    let sink = match result_callback {
        Some(callback) => SearchResultCallbackSink(callback),
        None => return MissingCallback,
    };

    match Searcher::new().search_file(&matcher, &file, sink) {
        Ok(_) => return Success,
        Err(_) => return ErrorFromCallback,
    };
}

// Defines the various types and enums used by this wrapper library
mod types {
    use grep::searcher::{Searcher, Sink, SinkError, SinkMatch};
    use std::fmt;
    use std::os::raw::c_int;

    // For use returning back through the FFI.
    // Note that the bytes inside are NOT nul-terminated!
    #[repr(C)]
    pub struct SearchResult {
        pub line_number: c_int,
        pub bytes: *const u8, // NOT nul-terminated!
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
    pub type SearchResultCallbackFn = extern "C" fn(SearchResult) -> bool;

    pub struct SearchResultCallbackSink(pub SearchResultCallbackFn);

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

    impl Sink for SearchResultCallbackSink {
        type Error = CallbackError;

        fn matched(
            &mut self,
            _searcher: &Searcher,
            matched: &SinkMatch,
        ) -> Result<bool, CallbackError> {
            let result = SearchResult {
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
    use grep::regex::RegexMatcher;

    use std::ffi::CStr;
    use std::fs::File;
    use std::os::raw::c_char;

    use crate::types::*;

    // Either opens the file with the given name, or returns an error code to pass out of the library
    pub fn open_filename(filename: Option<*const c_char>) -> Result<File, SearchStatusCode> {
        use SearchStatusCode::*;

        // Java owns the string, so we view the text as a &CStr reference rather than an owned CString
        let filename: &CStr = match filename {
            None => return Err(MissingFilename),
            Some(filename_ptr) => unsafe { CStr::from_ptr(filename_ptr) },
        };

        let filename: &str = match filename.to_str() {
            Ok(filename) => filename,
            Err(_) => return Err(ErrorCouldNotOpenFile),
        };

        match File::open(filename) {
            Ok(file) => Ok(file),
            Err(_) => Err(ErrorCouldNotOpenFile),
        }
    }

    // Either generates a regular-expression matcher from the given C-style string,
    // or returns an error code to pass out of the library
    pub fn parse_search_text(
        search_text: Option<*const c_char>,
    ) -> Result<RegexMatcher, SearchStatusCode> {
        use SearchStatusCode::*;

        // Java owns the string, so we view the text as a &CStr reference rather than an owned CString
        let search_text: &CStr = match search_text {
            None => return Err(MissingSearchText),
            Some(search_text_ptr) => unsafe { CStr::from_ptr(search_text_ptr) },
        };

        let search_text: &str = match search_text.to_str() {
            Ok(search_text) => search_text,
            Err(_) => return Err(ErrorBadPattern),
        };

        match RegexMatcher::new(search_text) {
            Ok(regex) => Ok(regex),
            Err(_) => return Err(ErrorBadPattern),
        }
    }

    #[cfg(test)]
    mod tests {
        use super::*;
        use std::ffi::CString;

        #[test]
        fn test_opening_bee_movie_script() {
            let filename = CString::new("bee_movie.txt").unwrap();
            let file = open_filename(Some(filename.as_ptr()));
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
                open_filename(Some(filename.as_ptr()))
                    .expect_err("Should not have been able to open missing file")
            );
        }

        #[test]
        fn test_opening_null_filename_returns_appropriate_error_code() {
            assert_eq!(
                SearchStatusCode::MissingFilename,
                open_filename(None)
                    .expect_err("Should not have been able to open a file with a null filename")
            );
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::*;

    #[test]
    fn test_search_for_bees_without_error() {
        let filename = CString::new("bee_movie.txt")
            .expect("Could not represent \"bee_movie.txt\" as a CString");
        let search_pattern =
            CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
        let callback = always_succeeding_callback;

        let result_code = search_file(
            Some(filename.as_ptr()),
            Some(search_pattern.as_ptr()),
            Some(callback),
        );

        assert_eq!(SearchStatusCode::Success, result_code,
            "When the callback returns true to indicate success, the extern search_file function should always return {:?}", SearchStatusCode::Success);
    }

    #[test]
    fn test_search_for_bees_returns_callback_error_code_when_callback_returns_false() {
        let filename = CString::new("bee_movie.txt")
            .expect("Could not represent \"bee_movie.txt\" as a CString");
        let search_pattern =
            CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
        let callback = always_failing_callback;

        let result_code = search_file(
            Some(filename.as_ptr()),
            Some(search_pattern.as_ptr()),
            Some(callback),
        );

        assert_eq!(SearchStatusCode::ErrorFromCallback, result_code,
            "When the callback returns false to indicate an error, the extern search_file function should always return {:?}", SearchStatusCode::ErrorFromCallback);
    }

    #[test]
    fn test_search_for_bees_returns_could_not_open_file_error_code_when_searching_missing_file() {
        let filename = CString::new("non_existant_file.txt")
            .expect("Could not represent \"non_existant_file.txt\" as a CString");
        let search_pattern =
            CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
        let callback = always_succeeding_callback;

        let result_code = search_file(
            Some(filename.as_ptr()),
            Some(search_pattern.as_ptr()),
            Some(callback),
        );

        assert_eq!(SearchStatusCode::ErrorCouldNotOpenFile, result_code,
            "When passing the name of a file that does not exist, the extern search_file function should always return {:?}", SearchStatusCode::ErrorCouldNotOpenFile);
    }

    #[test]
    fn test_search_using_null_filename_returns_missing_filename_error_code() {
        let search_pattern =
            CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
        let callback = always_succeeding_callback;

        let result_code = search_file(None, Some(search_pattern.as_ptr()), Some(callback));

        assert_eq!(SearchStatusCode::MissingFilename, result_code,
            "When passing a null filename, the extern search_file function should always return {:?}", SearchStatusCode::MissingFilename);
    }

    #[test]
    fn test_search_for_null_search_text_returns_missing_search_text_error_code() {
        let filename = CString::new("bee_movie.txt")
            .expect("Could not represent \"bee_movie.txt\" as a CString");
        let callback = always_succeeding_callback;

        let result_code = search_file(Some(filename.as_ptr()), None, Some(callback));

        assert_eq!(SearchStatusCode::MissingSearchText, result_code,
            "When passing null search text, the extern search_file function should always return {:?}", SearchStatusCode::MissingSearchText);
    }

    #[test]
    fn test_search_with_null_callback_returns_missing_callback_error_code() {
        let filename = CString::new("bee_movie.txt")
            .expect("Could not represent \"bee_movie.txt\" as a CString");
        let search_pattern =
            CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");

        let result_code = search_file(Some(filename.as_ptr()), Some(search_pattern.as_ptr()), None);

        assert_eq!(SearchStatusCode::MissingCallback, result_code,
            "When passing a null callback, the extern search_file function should always return {:?}", SearchStatusCode::MissingCallback);
    }

    #[test]
    fn test_calling_callback_single_element() {
        let filename = CString::new("bee_movie.txt").unwrap();
        let search_text = CString::new("graduation").unwrap(); // only on line 13
        let callback: SearchResultCallbackFn = match_graduation_on_line_13_callback;

        // testing inherently unsafe code, this is fine (without locks) if this test is only run once each execution
        unsafe { NUM_GRADUATIONS = 0 };
        let result_code = search_file(
            Some(filename.as_ptr()),
            Some(search_text.as_ptr()),
            Some(callback),
        );
        assert_eq!(SearchStatusCode::Success, result_code,
            "There is one match for \"graduation\" in the Bee Movie script, so a search for that literal should yield a successful result");
        assert_eq!(
            1,
            // testing inherently unsafe code, this is fine if this test is only run once each execution
            unsafe { NUM_GRADUATIONS },
            "The search function indicated success, but didn't actually call the callback."
        );
    }

    extern "C" fn always_succeeding_callback(_: SearchResult) -> bool {
        true
    }

    extern "C" fn always_failing_callback(_: SearchResult) -> bool {
        false
    }

    extern "C" fn match_graduation_on_line_13_callback(result: SearchResult) -> bool {
        assert_eq!(13, result.line_number);
        let bytes = unsafe { std::slice::from_raw_parts(result.bytes, result.num_bytes as usize) };
        let text = std::str::from_utf8(bytes)
            .expect("The bytes passed to the result callback were not a valid UTF-8 string");
        assert!(text.contains("graduation"));
        // testing inherently unsafe code, this is fine if this test is only run once each execution
        unsafe { NUM_GRADUATIONS += 1 };
        true
    }

    //testing inherently unsafe code, this is fine if this test is only run once each execution
    static mut NUM_GRADUATIONS: u32 = 0; // consider using a mutex to guarantee this is not run concurrently?
}
