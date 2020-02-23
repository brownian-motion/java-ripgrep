use std::os::raw::c_char;

pub use crate::types::*;

#[no_mangle]
pub extern "C" fn search_path(
    // every Java type is nullable, represented here as an Option<*type>
    filename: *const c_char,
    search_text: *const c_char,
    result_callback: Option<SearchResultCallbackFn>,
) -> SearchStatusCode {
    use SearchStatusCode::*;

    match core::search_path(filename, search_text, result_callback) {
        Ok(_) => return Success,
        Err(code) => return code,
    }
}

#[no_mangle]
#[deprecated(since = "0.2.0", note = "please use `search_path` instead")]
pub extern "C" fn search_file(
    // every Java type is nullable, represented here as an Option<*type>
    filename: *const c_char,
    search_text: *const c_char,
    result_callback: Option<SearchResultCallbackFn>,
) -> SearchStatusCode {
    search_path(filename, search_text, result_callback)
}

#[no_mangle]
#[deprecated(since = "0.2.0", note = "please use `search_path` instead")]
pub extern "C" fn search_dir(
    // every Java type is nullable, represented here as an Option<*type>
    filename: *const c_char,
    search_text: *const c_char,
    result_callback: Option<SearchResultCallbackFn>,
) -> SearchStatusCode {
    search_path(filename, search_text, result_callback)
}

mod core {
    use std::os::raw::c_char;
    use std::path::Path;
    use std::result::Result;

    use grep::regex::RegexMatcher;
    use grep::searcher::Searcher;
    use walkdir::*;
    use walkdir::DirEntry;

    use crate::parse::*;
    use crate::types::*;

    pub fn search_path(
        // every Java type is nullable, represented here as an Option<*type>
        filename: *const c_char,
        search_text: *const c_char,
        result_callback: Option<SearchResultCallbackFn>,
    ) -> Result<(), SearchStatusCode> {
        use SearchStatusCode::*;

        let path = parse_path(filename)?;
        let matcher: RegexMatcher = parse_search_text(search_text)?;
        let callback = result_callback.ok_or(MissingCallback)?;

        match path {
            file if file.is_file() => search_file(&file, matcher, callback),
            dir if dir.is_dir() => search_dir(&dir, matcher, callback),
            _ => Err(ErrorCouldNotOpenFile),
        }
    }

    fn search_file(
        file: &Path,
        matcher: RegexMatcher,
        callback: SearchResultCallbackFn,
    ) -> Result<(), SearchStatusCode> {
        // the Sink type accepts search results from ripgrep
        let sink = SearchResultCallbackSink(callback, file);

        Searcher::new()
            .search_path(&matcher, file, sink)
            .map(|_| ())
            .map_err(|_| SearchStatusCode::ErrorFromCallback)
    }

    fn search_dir(
        dir: &Path,
        matcher: RegexMatcher,
        callback: SearchResultCallbackFn,
    ) -> Result<(), SearchStatusCode> {
        use SearchStatusCode::*;

        let walker = WalkDir::new(&dir).into_iter();
        for entry in walker.filter_entry(|e| !is_hidden(e)) {
            let entry = entry.map_err(|_| ErrorCouldNotOpenFile)?;

            if !entry.file_type().is_file() {
                continue;
            }

            // Pass cloned sink from the outer scope.
            // This is probably fine, since we're just cloning a function pointer.
            // We'll trust our wrapper class to handle being called by multiple threads at once.
            Searcher::new()
                .search_path(
                    &matcher,
                    entry.path(),
                    SearchResultCallbackSink(callback, entry.path()),
                )
                .map_err(|_| ErrorFromCallback)?;
        }
        Ok(())
    }

    fn is_hidden(entry: &DirEntry) -> bool {
        entry
            .file_name()
            .to_str()
            .map(|s| s.starts_with("."))
            .unwrap_or(false)
    }
}
