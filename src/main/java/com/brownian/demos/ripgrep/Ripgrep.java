package com.brownian.demos.ripgrep;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Pattern;


/**
 * A nice (i.e. hygienic) wrapper around {@code RipgrepNativeMapping},
 * which interfaces with native Ripgrep code through JNA.
 *
 * This class is intended to expose that functionality in a nice way for Java users.
 */
public class Ripgrep
{
	public static void searchFile(Path file, Pattern pattern, Consumer<SearchResult> resultConsumer) throws RipgrepException
	{
		if (file == null)
		{
			throw new IllegalArgumentException("Missing file name");
		}
		if (pattern == null)
		{
			throw new IllegalArgumentException("Missing search pattern");
		}
		if (resultConsumer == null)
		{
			throw new IllegalArgumentException("Missing search result consumer callback");
		}
		String nativeFilename = file.toString();
		String nativePattern = pattern.toString();

		RipgrepNativeMapping.SearchResultCallback nativeCallback = result -> {
			try
			{
				resultConsumer.accept(new SearchResult(result));
				return true; // indicates success
			}
			catch (Exception e)
			{
				return false; // indicates failure
				// TODO: pass the Exception out through the native code,
				//       so it can be re-thrown once we re-enter Java code
			}
		};

		final int resultStatusCode = RipgrepNativeMapping.LIB.search_file(nativeFilename, nativePattern, nativeCallback);
		switch (resultStatusCode)
		{
			case RipgrepNativeMapping.ErrorCodes.SUCCESS:
				return;
			case RipgrepNativeMapping.ErrorCodes.MISSING_FILENAME:
				throw new IllegalStateException("Filename passed to native code was missing or could not be read; this should not happen");
			case RipgrepNativeMapping.ErrorCodes.MISSING_SEARCH_TEXT:
				throw new IllegalStateException("Search text passed to native code was missing or could not be read; this should not happen");
			case RipgrepNativeMapping.ErrorCodes.MISSING_CALLBACK:
				throw new IllegalStateException("Callback, wrapped for use in native code, was missing or could not be called; this should not happen");
			case RipgrepNativeMapping.ErrorCodes.ERROR_BAD_PATTERN:
				throw new RipgrepException("Invalid search text \"" + nativePattern + "\". Ripgrep and JavaSE do not implement the same regex library, so Ripgrep may not support all of the same features.");
			case RipgrepNativeMapping.ErrorCodes.ERROR_COULD_NOT_OPEN_FILE:
				throw new IllegalArgumentException("Ripgrep could not open or read file \"" + nativeFilename + "\"");
			case RipgrepNativeMapping.ErrorCodes.ERROR_FROM_RIPGREP:
				throw new RipgrepException("An error was raised by Ripgrep itself. Due to the nature of the FFI interface, details are not available.");
			case RipgrepNativeMapping.ErrorCodes.ERROR_FROM_CALLBACK:
				throw new RipgrepException("An exception was thrown by the provided callback " + resultConsumer.toString());
			default:
				throw new RipgrepException("An unrecognized status code (" + resultStatusCode + ") was returned by Ripgrep");
		}

	}

	/**
	 * Represents the same data as {@code RipgrepNativeMapping#SearchResult},
	 * but without maintaining references to native memory.
	 * This allocates an extra String for each result, but
	 */
	public static class SearchResult
	{
		private int lineNumber;
		private String text;

		public int getLineNumber()
		{
			return this.lineNumber;
		}

		public String getText()
		{
			return this.text;
		}

		public SearchResult(int lineNumber, String text)
		{
			this.lineNumber = lineNumber;
			this.text = text;
		}

		private SearchResult(int lineNumber, byte[] utf8Bytes)
		{
			this(lineNumber, new String(utf8Bytes, StandardCharsets.UTF_8));
		}

		private SearchResult(RipgrepNativeMapping.SearchResult nativeResult)
		{
			// we need to consume this in a way that does not maintain any references to the native struct;
			// therefore we have to straight-up copy everything
			this(nativeResult.line_number, nativeResult.bytes.getByteArray(0, nativeResult.num_bytes));
		}
	}

	public static class RipgrepException extends Exception
	{
		private RipgrepException(String reason)
		{
			super(reason);
		}

		private RipgrepException(String reason, Throwable cause)
		{
			super(reason, cause);
		}
	}
}