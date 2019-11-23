package com.brownian.demos.ripgrep;

import java.nio.charset.StandardCharsets;

import com.sun.jna.Memory;

public class Main
{
	// TODO: make this an interesting demo, with a GUI!

	public static void main(String[] args)
	{
		RipgrepNativeMapping.SearchResultCallback callback = result -> {
			System.out.println("Result received!");
			System.out.flush();
			return true;
		};

		search_file("bee_movie.txt", "[Bb]ee", callback);
	}

	private static int search_file(String filename, String searchText, RipgrepNativeMapping.SearchResultCallback callback)
	{
		System.out.printf("Searching for \"%s\" in file \"%s\" from Java...%n", searchText, filename);
		System.out.flush();

		int statusCode = RipgrepNativeMapping.LIB.search_file(
				filename,
				searchText,
				callback
		);

		System.out.println("Finished ripgrep search with status " + statusCode);

		return statusCode;
	}

	private static Memory convertToCString(String str)
	{
		// A normal java.lang.String would trigger a MemoryException when used in native code,
		// because of how the JVM manages its memory internally.
		// This ensures that 1) the character encoding is what native code expects,
		// and 2) strings are NUL-terminated like C-style libraries would expect.
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
		long numBytes = bytes.length;
		Memory memory = new Memory(numBytes + 1);
		for (int i = 0; i < numBytes; i++)
		{
			memory.setByte(i, bytes[i]);
		}
		memory.setByte(numBytes, (byte) 0); // NUL-terminate string
		System.out.printf("Fit %d bytes into a buffer of size %d: %s (%s)%n", numBytes + 1, memory.size(), memory.dump(), memory);
		return memory;
	}
}