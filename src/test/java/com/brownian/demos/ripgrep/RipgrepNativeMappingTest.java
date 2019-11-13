package com.brownian.demos.ripgrep;

import org.junit.Test;
import static org.junit.Assert.*;

public class RipgrepNativeMappingTest {
	@Test
	public void test_searchBeeMovieScriptForBees() {
		final int[] numCalls = { 0 }; // necessary bc lambda captures must be final
		RipgrepNativeMapping.SearchResultCallback callback = result -> {
			numCalls[0]++;
			return true;
		};

		int status = RipgrepNativeMapping.LIB.search_file("bee_movie.txt", "[Bb]ee", callback);

		assertEquals(RipgrepNativeMapping.ErrorCodes.SUCCESS, status);
		assertEquals("There should be 82 lines with \"bee\" in them in the entire script of Bee Movie",
		             82, numCalls[0]);
	}

	@Test
	public void test_returnsErrorFromCallback_whenCallbackReturnsFalse() {
		int[] numCalls = { 0 }; // necessary bc lambda captures must be final
		RipgrepNativeMapping.SearchResultCallback callback = result -> {
			numCalls[0]++;
			return false;
		};

		int status = RipgrepNativeMapping.LIB.search_file("bee_movie.txt", "[Bb]ee", callback);

		assertEquals(RipgrepNativeMapping.ErrorCodes.ERROR_FROM_CALLBACK, status);
		assertEquals(1, numCalls[0]);
	}

	@Test
	public void test_returnsMissingCallback_whenCallbackIsNull() {
		int status = RipgrepNativeMapping.LIB.search_file("bee_movie.txt", "[Bb]ee", null);

		assertEquals(RipgrepNativeMapping.ErrorCodes.MISSING_CALLBACK, status);
	}

	@Test
	public void test_returnsMissingFilename_whenFilenameIsNull() {
		RipgrepNativeMapping.SearchResultCallback callback = result -> {
			fail("This callback should never be called!");
			return true;
		};

		int status = RipgrepNativeMapping.LIB.search_file(null, "[Bb]ee", callback);

		assertEquals(RipgrepNativeMapping.ErrorCodes.MISSING_FILENAME, status);
	}

	@Test
	public void test_returnsMissingSearchText_whenSearchTextIsNull() {
		RipgrepNativeMapping.SearchResultCallback callback = result -> {
			fail("This callback should never be called!");
			return true;
		};

		int status = RipgrepNativeMapping.LIB.search_file("bee_movie.txt", null, callback);

		assertEquals(RipgrepNativeMapping.ErrorCodes.MISSING_SEARCH_TEXT, status);
	}
}