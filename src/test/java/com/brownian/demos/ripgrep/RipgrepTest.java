package com.brownian.demos.ripgrep;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.Ignore;
import org.junit.Test;

public class RipgrepTest
{
	private static final String[] NON_EXISTENT_FILES = { "missing_file.txt", "doesn'tExist!.dat" };

	@Test
	public void searchFile_searchingUsingCharacterClass_successfulCallbackIsCalled() throws Ripgrep.RipgrepException
	{

		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);

		Ripgrep.searchFile(Paths.get("bee_movie.txt"), Pattern.compile("[Bb]ee"), resultConsumer);

		verify(resultConsumer, times(82)).accept(any(Ripgrep.SearchResult.class));
	}

	@Test
	public void searchFile_searchingUsingLiteralPattern_successfulCallbackIsCalled() throws Ripgrep.RipgrepException
	{

		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);

		Ripgrep.searchFile(Paths.get("bee_movie.txt"), Pattern.compile("bee"), resultConsumer);

		verify(resultConsumer, times(66)).accept(any(Ripgrep.SearchResult.class));
	}

	@Test
	@Ignore("This feature hasn't been implemented yet, so I won't enable the test covering it.")
	public void searchFile_throwsWrappedException_whenCallbackThrowsException()
	{
		// done so I can verify() that it was called
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);
		Throwable toBeThrown = new Throwable("I get thrown inside the callback");
		doThrow(toBeThrown).when(resultConsumer).accept(any(Ripgrep.SearchResult.class));

		try
		{
			Ripgrep.searchFile(Paths.get("bee_movie.txt"), Pattern.compile("[Bb]ee"), resultConsumer);
			fail("Because the callback throws an exception, the Ripgrep search function should throw an Exception");
		}
		catch (Ripgrep.RipgrepException e)
		{
			// verify() first because the test logic relies on our assumptions holding,
			// and verify() would expose flaws in our assumptions
			verify(resultConsumer, times(1)).accept(any(Ripgrep.SearchResult.class));

			assertSame("The cause of the thrown RipgrepException should be the original Throwable thrown by the callback!", toBeThrown, e.getCause());
		}
	}


	@Test
	public void searchFile_throwsRipgrepException_whenCallbackThrowsException()
	{
		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);
		RuntimeException toBeThrown = new RuntimeException("I get thrown inside the callback");
		doThrow(toBeThrown).when(resultConsumer).accept(any(Ripgrep.SearchResult.class));

		try
		{
			Ripgrep.searchFile(Paths.get("bee_movie.txt"), Pattern.compile("[Bb]ee"), resultConsumer);
			fail("Ripgrep search function should throw exception when provided search result consumer callback throws Exception");
		}
		catch (Ripgrep.RipgrepException e)
		{
			verify(resultConsumer, times(1)).accept(any(Ripgrep.SearchResult.class));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchFile_throwsIllegalArgumentException_whenNullPatternPassed() throws Ripgrep.RipgrepException
	{
		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);
		Ripgrep.searchFile(Paths.get("bee_movie.txt"), null, resultConsumer);
		verifyZeroInteractions(resultConsumer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchFile_throwsIllegalArgumentException_whenNullFilePathPassed() throws Ripgrep.RipgrepException
	{
		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);
		Ripgrep.searchFile(null, Pattern.compile("[Bb]ee"), resultConsumer);
		verifyZeroInteractions(resultConsumer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchFile_throwsIllegalArgumentException_whenNullCallbackPassed() throws Ripgrep.RipgrepException
	{
		Ripgrep.searchFile(Paths.get("bee_movie.txt"), Pattern.compile("[Bb]ee"), null);
	}

	@Test
	public void searchFile_throwsRipgrepExceptionContainingFilename_whenSearchingMissingFilename()
	{
		// using Mockito mock so I can verify() that the callback was never called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);
		for (String missingFile : NON_EXISTENT_FILES)
		{
			try
			{
				Ripgrep.searchFile(Paths.get(missingFile), Pattern.compile("[Bb]ee"), resultConsumer);
			}
			catch (Ripgrep.RipgrepException e)
			{
				verifyZeroInteractions(resultConsumer);
				assertThat(e.getMessage(), containsString(missingFile));
			}
		}
	}

	@Test
	public void searchFile_throwsRipgrepExceptionContainingPattern_whenSearchingWithInvalidPattern()
	{

		// using Mockito mock so I can verify() that the callback was never called
		//noinspection unchecked
		Consumer<Ripgrep.SearchResult> resultConsumer = mock(Consumer.class);
		for (String badPattern : new String[] { Pattern.quote("\\Q\\E quotes are invalid in ripgrep") })
		{
			try
			{
				Ripgrep.searchFile(Paths.get("bee_movie.txt"), Pattern.compile(badPattern), resultConsumer);
			}
			catch (Ripgrep.RipgrepException e)
			{
				verifyZeroInteractions(resultConsumer);
				assertThat(e.getMessage(), containsString(badPattern));
			}
		}
	}
}