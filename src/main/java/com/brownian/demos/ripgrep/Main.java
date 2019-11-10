package com.brownian.demos.ripgrep;

public class Main {
	public static void main(String[] args) {
		RipgrepNativeMapping.SearchResultCallback callback = result -> { System.out.println("Result received!"); return true; };

		System.out.println("Hello, ripgrep!");
	}
}