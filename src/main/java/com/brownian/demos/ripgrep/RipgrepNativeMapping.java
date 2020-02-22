package com.brownian.demos.ripgrep;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * Declares the API of the native dynamic library we're using.
 * <p>
 * Because we're using JNA, we can declare a {@link Library} that uses simple Java types, like Strings.
 * An instance of this interface is available as {@link RipgrepNativeMapping#LIB}.
 * <p>
 * Note that we can declare structs to pass around by extending {@link Structure},
 * and specify pass-by-value or pass-by-reference semantics
 * by implementing {@link Structure.ByValue} and {@link Structure.ByReference}.
 * <p>
 * We can specify callback functions by making an interface with exactly one method, which extends {@link Callback}.
 * <p>
 * Using JNA makes this wrapper interface more convenient to write, but it's not necessarily ergonomic for
 * the end users of this library. In particular, {@link SearchResultCallback}s must take care not to throw Exceptions,
 * and discard any references to the memory provided to them before the callback ends.
 * To alleviate this burden, this interface is package-protected
 * and wrapped by the {@link RipgrepSearcher} class, which is much more natural to use.
 */
interface RipgrepNativeMapping extends Library
{
	String JNA_LIBRARY_NAME = "ripgrep_ffi";

	RipgrepNativeMapping LIB = Native.load(JNA_LIBRARY_NAME, RipgrepNativeMapping.class);

	int search_file(
			String filename,
			String search_text, // Rust-style regex
			SearchResultCallback callback
	);

	int search_dir(
			String dirname,
			String search_text, // Rust-style regex
	    SearchResultCallback callback
	);


	/**
	 * A callback which receives matches from ripgrep, by-reference.
	 * The memory underlying this match is owned by the native code,
	 * so references to it must be dropped before this callback exits.
	 * Additionally, this callback must not throw Exceptions,
	 * or else it will corrupt the native stack during unwinding.
	 *
	 * @apiNote Because of the care one must take to implement this, it is only used internally within this package.
	 */
	interface SearchResultCallback extends Callback {
		boolean callback(SearchResult.ByReference result);
	}

	/**
	 * Represents a search result.
	 * Contains a pointer to natively-owned UTF-8 bytes containing the line with a match and the line number it was matched on.
	 */
	class SearchResult extends Structure {
		public String file_name;
		public int line_number;
		public Pointer bytes;
		public int num_bytes;

		@Override
		public List<String> getFieldOrder() {
			return Arrays.asList("file_name", "line_number", "bytes", "num_bytes");
		}

		public static class ByReference extends SearchResult implements Structure.ByReference {
		}

		public static class ByValue extends SearchResult implements Structure.ByValue {
		}
	}

	/**
	 * Declares constants matching each error code returned by the library.
	 */
	final class ErrorCodes {
		// Mirrors the C-style enums defined in the native library
		public static final int
		SUCCESS = 0,
		MISSING_FILENAME = 1,
		MISSING_SEARCH_TEXT = 2,
		MISSING_CALLBACK = 3,
		// Failure from inside ripgrep:
		ERROR_BAD_PATTERN = 11,
		ERROR_COULD_NOT_OPEN_FILE = 12,
		ERROR_FROM_RIPGREP = 13,
		// Failure from inside the callback:
		ERROR_FROM_CALLBACK = 21;

		// Since this is a utility class, it should not be instantiated.
		private ErrorCodes() {
		}
	}
}