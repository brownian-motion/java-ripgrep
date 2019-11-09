extern crate grep;
use grep::regex::RegexMatcher;
use grep::searcher::sinks::UTF8;
use grep::searcher::Searcher;

use std::fs::File;
use std::io;

fn main() {
    search_for_bees(io::stdout()).unwrap();
}

fn search_for_bees<W: io::Write>(mut out: W) -> Result<(), io::Error> {
    let sink = UTF8(|line: u64, text: &str| {
        writeln!(out, "Match at line {}: {}", line, text)?;
        Ok(true)
    });

    // finds every "bee" in Bee Movie
    let matcher = RegexMatcher::new("[Bb]ee").expect("Could not form bee-matching RegexMatcher");

    let subject = File::open("bee_movie.txt")
        .expect("Could not open the entire script of Bee Movie in bee_movie.txt");

    Searcher::new().search_file(&matcher, &subject, sink)?;

    Ok(())
}
