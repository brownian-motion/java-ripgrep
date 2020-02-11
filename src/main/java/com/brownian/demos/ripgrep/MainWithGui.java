package com.brownian.demos.ripgrep;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;

public class MainWithGui
{
	public static void main(String[] args)
	{
		JFrame frame = new JFrame("Ripgrep search");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JPanel frameContentRoot = new JPanel(new BorderLayout());
		frame.setContentPane(frameContentRoot);

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();

		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weighty = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		form.add(new JLabel("Search Text:"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		JTextField searchTextField = new JTextField();
		form.add(searchTextField, constraints);

		constraints.gridx = 0;
		constraints.gridy = 1;
		constraints.weightx = 0;
		form.add(new JLabel("Search Path:"), constraints);

		constraints.gridx = 1;
		constraints.weightx = 1;
		JTextField dirPathField = new JTextField();
		form.add(dirPathField, constraints);

		constraints.gridx = 2;
		constraints.weightx = 0;
		JButton dirChooserButton = new JButton();
		dirChooserButton.setText("...");
		dirChooserButton.addActionListener(action -> {
			JFileChooser dirChooser = new JFileChooser();
			dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (dirChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
			{
				dirPathField.setText(dirChooser.getSelectedFile().getPath());
			}
		});
		form.add(dirChooserButton, constraints);

		constraints.gridx = 0;
		constraints.gridy = 3;
		constraints.gridwidth = 3;
		constraints.insets = new Insets(20, 20, 20, 20);
		constraints.anchor = GridBagConstraints.PAGE_END;
		JButton searchButton = new JButton("Search");
		form.add(searchButton, constraints);

		JLabel status = new JLabel("Ready");
		constraints.gridy = 4;
		constraints.weighty = 0;
		form.add(status, constraints);

		JPanel searchResults = new JPanel();
		searchResults.setLayout(new BoxLayout(searchResults, BoxLayout.PAGE_AXIS));
		JScrollPane searchResultsWrapper = new JScrollPane(searchResults);
		searchResultsWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchResultsWrapper.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		searchButton.addActionListener(event -> {
			searchResults.removeAll();
			try
			{
				status.setText("Searching...");
				SwingWorker<Void, Ripgrep.SearchResult> ripgrepWorker = new RipgrepSearchWorker(searchResults, status, dirPathField.getText(), searchTextField.getText());
				ripgrepWorker.execute();
			}
			catch (Exception e)
			{
				displayErrorResult(e, searchResults);
			}
		});

		frame.add(form, BorderLayout.NORTH);
		frame.add(searchResultsWrapper, BorderLayout.CENTER);

		// native look and feel
		JFrame.setDefaultLookAndFeelDecorated(true);
		frame.setSize(800, 800);

		frame.setVisible(true);
	}

	private static void displayErrorResult(Exception e, JPanel container)
	{
		JLabel errorLabel = new JLabel(e.getMessage());
		errorLabel.setForeground(Color.RED);
		container.add(errorLabel, 0);
	}

	private static class RipgrepSearchWorker extends SwingWorker<Void, Ripgrep.SearchResult>
	{
		private final JPanel searchResults;
		private final JLabel status;
		private final Pattern regex;
		private final Path searchPath;

		public RipgrepSearchWorker(JPanel resultPanel, JLabel status, String dirPath, String searchPattern)
		{
			this.searchResults = resultPanel;
			this.status = status;
			this.regex = Pattern.compile(searchPattern);
			this.searchPath = Paths.get(dirPath);
		}

		@Override
		public Void doInBackground()
		{
			try
			{
				Ripgrep.searchDir(searchPath, regex, this::publish);
			}
			catch (Exception e)
			{
				displayErrorResult(e, this.searchResults);
			}
			return null;
		}

		@Override protected void process(List<Ripgrep.SearchResult> chunks)
		{
			status.setText("Processing...");
			if (chunks != null)
			{
				chunks.stream()
						.map(result -> result.getFileName() + " \t " + result.getLineNumber() + ":\t " + result.getText())
						.map(JLabel::new)
						.forEach(searchResults::add);
			}
			status.setText("Searching...");
		}

		@Override protected void done()
		{
			status.setText("Done");
		}
	}
}
