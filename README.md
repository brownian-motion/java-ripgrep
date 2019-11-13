# Java-Rust FFI Demo: Ripgrep

This is a demo of FFI bindings between Java and Rust code using JNA.

To demonstrate a nontrivial, real-world use case of using native code in Java,
this library imports some of the functionality of the [ripgrep](https://github.com/BurntSushi/ripgrep) Rust library into Java to use the library like any other class.

While this is intended to represent a real-world use case,
this repo is primarily a demo and is not intended for production use.
It is licensed under [GPL 3](https://www.gnu.org/licenses/gpl-3.0.en.html), so feel free to use it however you like; just don't blame me for bugs!

## Installing
Because native code is not portable, the native side of this library must be re-compiled for each OS/architecture.
Since this code is written in Rust, you must compile it using [the Rust toolchain](https://rustup.rs/) before it can be used.
This demo was prepared using library version of ripgrep (`grep v0.2`) and compiled with `rust 1.39 (stable)`. For convenience, this repos uses [a maven build script for Rust code](src/build/java/com/github/drrb/javarust/build/CargoBuild.java).
To compile, you need `cargo` and `mvn` on your path.

## Contributing
I will happily accept any contributions that improve the codebase.
In particular, if you see a way to fix bugs, add tests or clear demos, or otherwise improve the codebase, please feel free to help out!
The best way to contribute would be by opening an Issue or Pull Request on Github.

## License
This repo is licensed under the [GNU Public License, version 3](https://www.gnu.org/licenses/gpl-3.0.en.html).

## References and related code
The inspiration (and initial reference) for establishing a Rust-Java FFI interface comes from [this repo by drrb](https://github.com/drrb/java-rust-example). I lifted `drrb`'s build script and maven structure directly, and modified it to build using `cargo` instead of `rustc`.

For more information about FFI interfaces in Rust, Java, or in general, the following resources  may be helpful:

- https://github.com/drrb/java-rust-example For the impetus of this repo
- https://doc.rust-lang.org/stable/std/ffi/ Rust's FFI reference
- https://www.eshayne.com/jnaex/index.html Several amazing examples of passing various data types across the FFI boundary using JNA
- https://github.com/java-native-access/jna Repo of the JNA project
- https://www.codepool.biz/java-jna-vs-jni-windows.html A good discussion of JNA vs JNI, the two most common approaches to using native code from Java
- https://cffi.readthedocs.io/en/latest/ How to write a Python package using native code
- https://www.baeldung.com/jni A simple guide to using JNI directly to reference a C++ library, without the JNA abstraction layer
- https://github.com/BurntSushi/ripgrep `ripgrep` on Github
- https://github.com/BurntSushi/ripgrep `ripgrep` on [crates.io](https://crates.io)