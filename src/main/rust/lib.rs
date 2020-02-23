extern crate grep;

pub use crate::ffi::*;
pub use crate::types::*;

// Defines the actual Foreign Function Interface
mod ffi;

// Defines the various types and enums used by this wrapper library
mod types;

// Handles parsing parameters passed to the library
mod parse;

// Runs unit tests
#[cfg(test)]
mod tests;
