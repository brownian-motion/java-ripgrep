package com.brownian.demos.ripgrep;

import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class Main
{
	// TODO: make this an interesting demo, with a GUI!

	public static void main(String[] args) throws Ripgrep.RipgrepException
	{
		Consumer<Ripgrep.SearchResult> callback = result -> {
			String line = String.format("%4d: %s", result.getLineNumber(), result.getText());
			for (char c : line.toCharArray())
			{
				try
				{
					System.out.print(c);
					Thread.sleep(3);
				}
				catch (InterruptedException ignored)
				{
				}
			}
			System.out.println();
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException ignored)
			{
			}
		};

		search_file("src/main/resources/bee_movie.txt", "[Bb]ee", callback);
	}

	private static void search_file(String filename, String searchText, Consumer<Ripgrep.SearchResult> callback) throws Ripgrep.RipgrepException
	{
		System.out.printf("Searching for \"%s\" in file \"%s\" using ripgrep from Java...%n", searchText, filename);
		System.out.flush();
		Ripgrep.searchFile(Paths.get(filename), Pattern.compile(searchText), callback);
	}
}