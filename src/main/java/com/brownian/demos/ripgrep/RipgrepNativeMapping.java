package com.brownian.demos.ripgrep;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

interface RipgrepNativeMapping extends Library
{
	final String JNA_LIBRARY_NAME = "ripgrep_test_playground";
	final NativeLibrary JNA_NATIVE_LIB = NativeLibrary.getInstance(JNA_LIBRARY_NAME);

	RipgrepNativeMapping LIB = Native.loadLibrary(JNA_LIBRARY_NAME, RipgrepNativeMapping.class);

	public interface SearchResultCallback extends Callback
	{
		boolean callback(SearchResult.ByReference result);
	}

	public int search_file(
	    String filename, // NUL-terminated C-string
	    String search_text, // NUL-terminated C-string
	    SearchResultCallback callback
	);

	public static class SearchResult extends Structure {
		public int line_number;
		public Pointer bytes;
		public int num_bytes;

		@Override
		public List<String> getFieldOrder() {
			return Arrays.asList("line_number", "bytes", "num_bytes");
		}

		public static class ByReference extends SearchResult implements Structure.ByReference {}

		public static class ByValue extends SearchResult implements Structure.ByValue {}
	}

	public static final class ErrorCodes {
		private ErrorCodes() {}

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
	}
}