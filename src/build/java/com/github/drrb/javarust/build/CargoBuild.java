/*
 * Copyright (C) 2015 drrb, paxromana96
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.drrb.javarust.build;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides the functionality to compile Rust crates
 * as a maven action.
 *
 * Modified from {@code drrb}'s original implementation
 * to build using {@code cargo} instead of {@code rustc}.
 */
public class CargoBuild {

    private static final Date EPOCH = new Date(0);
    private static final Path RUST_OUTPUT_DIR = Paths.get("target", "rust-libs");
    private static final Set<String> DYLIB_EXTENSIONS = new HashSet<String>(asList(".dylib", ".so", ".dll"));

    public static void main(String[] args) throws Exception {
        RUST_OUTPUT_DIR.toFile().mkdirs();
        CargoBuild.compile();
    }

    private static void compile() throws CargoBuildFailureException {
        System.out.println("Compiling rust crate...");
        try {
            Process process = cargoBuildProcess().inheritIO().start();
            process.waitFor();
            if (process.exitValue() != 0) {
                throw new CargoBuildFailureException(String.format("cargo exited nonzero (status code = %s)", process.exitValue()));
            }
            for (Path compiledRustLibrary : compiledRustLibraries()) {
                moveLibIntoClasspath(compiledRustLibrary);
            }
        } catch (IOException | InterruptedException ex) {
            throw new CargoBuildFailureException("Could not compile", ex);
        }
    }

    private static ProcessBuilder cargoBuildProcess() {
        List<String> commandParts;
        if (inNetbeans() && new File("/bin/bash").isFile()) {
            System.out.println("(running cargo via bash because we're in NetBeans)");
            commandParts = asList("/bin/bash", "-lc", String.format("cargo build --target-dir %s", RUST_OUTPUT_DIR));
        } else {
            commandParts = asList("cargo", "build", "--lib", "--target-dir", RUST_OUTPUT_DIR.toString());
        }
        System.out.format("Running command: %s%n", commandParts);
        return new ProcessBuilder(commandParts);
    }

    private static void moveLibIntoClasspath(Path library) throws IOException {
        Path outputDir = outputDir();
        outputDir.toFile().mkdirs();
        System.out.format("Installing %s into %s%n", library, outputDir);
        Files.copy(library, outputDir.resolve(library.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Determines the {@link Path} in which to store compiled native libraries
     * compiled for the current system's OS+architecture combo.
     * @return the {@link Path} to store the compiled libraries in
     */
    private static Path outputDir() {
        return Paths.get("target", "classes", osArchName());
    }

    /**
     * Determines the name of the current OS+architecture combo, for use in {@link #outputDir()}.
     * @return a file-safe name for the current OS+architecture combo
     */
    private static String osArchName() {
        return Os.getCurrent().jnaArchString();
    }

    /**
     * Returns a {@link List} of files compiled by cargo into {@link #RUST_OUTPUT_DIR}.
     * @return a {@link List} of all compiled dynamic libraries in the output directory
     * @throws IOException if the filesystem cannot be used to load paths to these files
     */
    private static List<Path> compiledRustLibraries() throws IOException {
        try (Stream<Path> dylibs = Files.find(RUST_OUTPUT_DIR, Integer.MAX_VALUE, (file, attrs) -> isDylib(file, attrs))) {
            return dylibs.collect(Collectors.toList());
        }
    }

    /**
     * Indicates whether or not this build task is being run via Netbeans,
     * so that {@link #cargoBuildProcess} can run an optimized build process.
     *
     * This method could stand to be improved, either using a factory/strategy pattern to return builders,
     * or by simply checking for {@code bash} on the path rather than assuming it can only be used if we're in Netbeans.
     *
     * @return {@code true} if this build task is being run via Netbeans, or {@code false} otherwise
     */
    private static boolean inNetbeans() {
        return System.getenv().entrySet()
               .stream()
        .anyMatch(envVars -> {
            String key = envVars.getKey();
            String value = envVars.getValue();
            return key.matches("JAVA_MAIN_CLASS_\\d+") && value.equals("org.netbeans.Main");
        });
    }

    /**
     * Indicates whether or not the file at the given path is a dynamic library.
     * @param path the path to a file
     * @param attributes the attributes of that file
     * @return {@code true} if the file looks like a dynamic library, and {@code false} otherwise
     */
    private static boolean isDylib(Path path, BasicFileAttributes attributes) {
        String pathString = path.toString();
        int lastPeriodIndex = pathString.lastIndexOf(".");
        if (lastPeriodIndex < 0) {
            return false;
        }
        String pathExtension = pathString.substring(lastPeriodIndex);
        return attributes.isRegularFile() && DYLIB_EXTENSIONS.contains(pathExtension);
    }

    /**
     * Defines basic details of various operating systems,
     * specifically the strings used by JNA to represent OS+architecture combos.
     *
     * These strings are used to determine in what subdirectories
     * to store/load dynamic libraries.
     */
    private enum Os {
        MAC_OS("mac", "darwin") {
            @Override
            public String jnaArchString() {
                return "darwin";
            }
        },
        WINDOWS("win") {
            @Override
            public String jnaArchString() {
                return currentIs64Bit() ? "win32-x86-64" : "win32-x86";
            }
        },
        GNU_SLASH_LINUX("nux") {
            @Override
            public String jnaArchString() {
                return currentIs64Bit() ? "linux-x86-64" : "linux-x86";
            }
        },
        UNKNOWN() {
            @Override
            public String jnaArchString()  {
                throw new RuntimeException("Unknown platform. Can't tell what platform we're running on!");
            }
        };
        private final String[] substrings;

        Os(String... substrings) {
            this.substrings = substrings;
        }

        public abstract String jnaArchString();

        public static Os getCurrent() {
            return Arrays.stream(values())
                   .filter(Os::isCurrent)
                   .findFirst()
                   .orElse(UNKNOWN);
        }

        public boolean isCurrent() {
            return Arrays.stream(substrings)
                   .anyMatch(substring -> currentOsString().contains(substring));
        }

        private static boolean currentIs64Bit() {
            return System.getProperty("os.arch").contains("64");
        }

        private static String currentOsString() {
            return System.getProperty("os.name", "unknown").toLowerCase(Locale.ENGLISH);
        }
    }

    private static class CargoBuildFailureException extends Exception {
        public CargoBuildFailureException(String message) {
            super(message);
        }

        public CargoBuildFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
