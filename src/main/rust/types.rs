// Defines the various types and enums used by this wrapper library
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
