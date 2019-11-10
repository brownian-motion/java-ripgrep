package com.brownian.demos.ripgrep;

import com.sun.jna.*;
import java.util.*;

public interface RipgrepNativeMapping extends Library {
	final String JNA_LIBRARY_NAME = "ripgrep_test_playground";
	final NativeLibrary JNA_NATIVE_LIB = NativeLibrary.getInstance(JNA_LIBRARY_NAME);

	RipgrepNativeMapping LIB = Native.loadLibrary(JNA_LIBRARY_NAME, RipgrepNativeMapping.class);

	public interface SearchResultCallback extends Callback {
		boolean callback(SearchResult result);
	}

	public int search_file(String filename, String search_text, SearchResultCallback callback);

	public static class SearchResult extends Structure {
		int line_number;
		byte[] bytes;
		int num_bytes;

		public List<String> getFieldOrder() {
			return Arrays.asList("line_number", "bytes", "num_bytes");
		}
	}
}