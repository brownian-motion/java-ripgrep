package com.brownian.demos.ripgrep;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class RipgrepSearcherTest
{
	public static final String BEE_MOVIE_FILE_NAME = "src/test/resources/bee_movie.txt";
	private static final String[] NON_EXISTENT_FILES = { "missing_file.txt", "doesn'tExist!.dat" };

	private RipgrepSearcher searcher;

	@Before
	public void setup()
	{
		searcher = new RipgrepSearcher();
	}

	@Test
	public void searchFile_searchingUsingCharacterClass_successfulCallbackIsCalled() throws RipgrepSearcher.RipgrepException
	{

		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);

		searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), "[Bb]ee", resultConsumer);

		verify(resultConsumer, times(82)).accept(any(RipgrepSearcher.SearchResult.class));
	}

	@Test
	public void searchFile_searchingUsingLiteralPattern_successfulCallbackIsCalled() throws RipgrepSearcher.RipgrepException
	{

		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);

		searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), "bee", resultConsumer);

		verify(resultConsumer, times(66)).accept(any(RipgrepSearcher.SearchResult.class));
	}

	@Test
	@Ignore("This feature hasn't been implemented yet, so I won't enable the test covering it.")
	public void searchFile_throwsWrappedException_whenCallbackThrowsException()
	{
		RuntimeException toBeThrown = new RuntimeException("I get thrown inside the callback");
		// spying this callback so I can verify() that it was called
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = spy(result -> {
			throw toBeThrown;
		});

		try
		{
			searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), "[Bb]ee", resultConsumer);
			fail("Because the callback throws an exception, the Ripgrep search function should throw an Exception");
		}
		catch (RipgrepSearcher.RipgrepException e)
		{
			// verify() first because the test logic relies on our assumptions holding,
			// and verify() would expose flaws in our assumptions
			verify(resultConsumer, times(1)).accept(any(RipgrepSearcher.SearchResult.class));

			assertSame("The cause of the thrown RipgrepException should be the original Throwable thrown by the callback!", toBeThrown, e.getCause());
		}
	}


	@Test
	public void searchFile_throwsRipgrepException_whenCallbackThrowsException()
	{
		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);
		RuntimeException toBeThrown = new RuntimeException("I get thrown inside the callback");
		doThrow(toBeThrown).when(resultConsumer).accept(any(RipgrepSearcher.SearchResult.class));

		try
		{
			searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), "[Bb]ee", resultConsumer);
			fail("Ripgrep search function should throw exception when provided search result consumer callback throws Exception");
		}
		catch (RipgrepSearcher.RipgrepException e)
		{
			verify(resultConsumer, times(1)).accept(any(RipgrepSearcher.SearchResult.class));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchFile_throwsIllegalArgumentException_whenNullPatternPassed() throws RipgrepSearcher.RipgrepException
	{
		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);
		searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), null, resultConsumer);
		verifyZeroInteractions(resultConsumer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchFile_throwsIllegalArgumentException_whenNullFilePathPassed() throws RipgrepSearcher.RipgrepException
	{
		// using Mockito mock so I can verify() that the callback was actually called
		//noinspection unchecked
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);
		searcher.search(null, "[Bb]ee", resultConsumer);
		verifyZeroInteractions(resultConsumer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchFile_throwsIllegalArgumentException_whenNullCallbackPassed() throws RipgrepSearcher.RipgrepException
	{
		searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), "[Bb]ee", null);
	}

	@Test
	public void searchFile_throwsExceptionContainingFilename_whenSearchingMissingFilename()
	{
		// using Mockito mock so I can verify() that the callback was never called
		//noinspection unchecked
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);
		for (String missingFile : NON_EXISTENT_FILES)
		{
			try
			{
				searcher.search(Paths.get(missingFile), "[Bb]ee", resultConsumer);
			}
			catch (Exception e)
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
		Consumer<RipgrepSearcher.SearchResult> resultConsumer = mock(Consumer.class);
		for (String badPattern : new String[] { Pattern.quote("\\Q\\E quotes are invalid in ripgrep") })
		{
			try
			{
				searcher.search(Paths.get(BEE_MOVIE_FILE_NAME), badPattern, resultConsumer);
				fail("Should have thrown a RipgrepException when trying to search with an invalid regex \"" + badPattern + "\"");
			}
			catch (RipgrepSearcher.RipgrepException e)
			{
				verifyZeroInteractions(resultConsumer);
				assertThat(e.getMessage(), containsString(badPattern));
			}
		}
	}
}