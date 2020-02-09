use std::ffi::*;
use std::ptr;

use super::*;

const BEE_MOVIE_FILE_NAME: &'static str = "src/test/resources/bee_movie.txt";

fn as_cstring(text: &str) -> CString {
    let error = format!("Could not represent {:?} as a CString", text);
    CString::new(text).expect(&error)
}

#[test]
fn test_search_for_bees_without_error() {
    let filename = as_cstring(BEE_MOVIE_FILE_NAME);
    let search_pattern =
        CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
    let callback = always_succeeding_callback;

    let result_code = search_file(filename.as_ptr(), search_pattern.as_ptr(), Some(callback));

    assert_eq!(SearchStatusCode::Success, result_code,
            "When the callback returns true to indicate success, the extern search_file function should always return {:?}", SearchStatusCode::Success);
}

#[test]
fn test_search_for_bees_returns_callback_error_code_when_callback_returns_false() {
    let filename = as_cstring(BEE_MOVIE_FILE_NAME);
    let search_pattern =
        CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
    let callback = always_failing_callback;

    let result_code = search_file(filename.as_ptr(), search_pattern.as_ptr(), Some(callback));

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

    let result_code = search_file(filename.as_ptr(), search_pattern.as_ptr(), Some(callback));

    assert_eq!(SearchStatusCode::ErrorCouldNotOpenFile, result_code,
            "When passing the name of a file that does not exist, the extern search_file function should always return {:?}", SearchStatusCode::ErrorCouldNotOpenFile);
}

#[test]
fn test_search_using_null_filename_returns_missing_filename_error_code() {
    let search_pattern =
        CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");
    let callback = always_succeeding_callback;

    let result_code = search_file(ptr::null(), search_pattern.as_ptr(), Some(callback));

    assert_eq!(
        SearchStatusCode::MissingFilename,
        result_code,
        "When passing a null filename, the extern search_file function should always return {:?}",
        SearchStatusCode::MissingFilename
    );
}

#[test]
fn test_search_for_null_search_text_returns_missing_search_text_error_code() {
    let filename = as_cstring(BEE_MOVIE_FILE_NAME);
    let callback = always_succeeding_callback;

    let result_code = search_file(filename.as_ptr(), ptr::null(), Some(callback));

    assert_eq!(
        SearchStatusCode::MissingSearchText,
        result_code,
        "When passing null search text, the extern search_file function should always return {:?}",
        SearchStatusCode::MissingSearchText
    );
}

#[test]
fn test_search_with_null_callback_returns_missing_callback_error_code() {
    let filename = as_cstring(BEE_MOVIE_FILE_NAME);
    let search_pattern =
        CString::new("[Bb]ee").expect("Could not represent \"[Bb]ee\" as a CString");

    let result_code = search_file(filename.as_ptr(), search_pattern.as_ptr(), None);

    assert_eq!(
        SearchStatusCode::MissingCallback,
        result_code,
        "When passing a null callback, the extern search_file function should always return {:?}",
        SearchStatusCode::MissingCallback
    );
}

#[test]
fn test_calling_callback_single_element() {
    let filename = as_cstring(BEE_MOVIE_FILE_NAME);
    let search_text = as_cstring("graduation"); // only on line 13
    let callback: SearchResultCallbackFn = match_graduation_on_line_13_callback;

    // testing inherently unsafe code, this is fine (without locks) if this test is only run once each execution
    unsafe { NUM_GRADUATIONS = 0 };
    let result_code = search_file(filename.as_ptr(), search_text.as_ptr(), Some(callback));
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
