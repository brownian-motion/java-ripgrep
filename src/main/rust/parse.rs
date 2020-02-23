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
            parse_search_text(ptr::null())
                .expect_err("Should not have been able to parse a search regex from a null string")
        );
    }
}
