package org.jabref.logic.util.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.logic.bibtexkeypattern.BibtexKeyPatternUtil;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.strings.StringUtil;
import org.jabref.model.util.FileHelper;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class CiteKeyBasedFileFinder implements FileFinder {

    private static final Log LOGGER = LogFactory.getLog(CiteKeyBasedFileFinder.class);
    private final boolean exactKeyOnly;

    CiteKeyBasedFileFinder(boolean exactKeyOnly) {
        this.exactKeyOnly = exactKeyOnly;
    }

    @Override
    public Multimap<BibEntry, Path> findAssociatedFiles(List<BibEntry> entries, List<Path> directories, List<String> extensions) {
        Objects.requireNonNull(directories);
        Objects.requireNonNull(entries);

        Multimap<BibEntry, Path> result = ArrayListMultimap.create();

        // First scan directories
        Set<Path> filesWithExtension = findFilesByExtension(directories, extensions);

        // Now look for keys
        nextFile:
        for (Path file : filesWithExtension) {
            String name = file.getFileName().toString();
            String nameWithoutExtension = FileUtil.getBaseName(name);

            // First, look for exact matches
            for (BibEntry entry : entries) {
                Optional<String> citeKey = entry.getCiteKeyOptional();
                if (StringUtil.isNotBlank(citeKey) && nameWithoutExtension.equals(citeKey.get())) {
                    result.get(entry).add(file);
                    continue nextFile;
                }
            }
            // If we get here, we did not find any exact matches. If non-exact matches are allowed, try to find one
            if (!exactKeyOnly) {
                for (BibEntry entry : entries) {
                    Optional<String> citeKey = entry.getCiteKeyOptional();
                    if (StringUtil.isNotBlank(citeKey) && matches(name, citeKey.get())) {
                        result.get(entry).add(file);
                        continue nextFile;
                    }
                }
            }
        }

        return result;
    }

    private boolean matches(String filename, String citeKey) {
        boolean startsWithKey = filename.startsWith(citeKey);
        if (startsWithKey) {
            // The file name starts with the key, that's already a good start
            // However, we do not want to match "JabRefa" for "JabRef" since this is probably a file belonging to another entry published in the same time / same name
            char charAfterKey = filename.charAt(citeKey.length());
            return !StringUtil.contains(BibtexKeyPatternUtil.CHARS, charAfterKey);
        }
        return false;
    }

    /**
     * Returns a list of all files in the given directories which have one of the given extension.
     */
    private Set<Path> findFilesByExtension(List<Path> directories, List<String> extensions) {
        Objects.requireNonNull(extensions, "Extensions must not be null!");

        BiPredicate<Path, BasicFileAttributes> isFileWithCorrectExtension = (path, attributes) ->
                !Files.isDirectory(path)
                        && extensions.contains(FileHelper.getFileExtension(path).orElse(""));

        Set<Path> result = new HashSet<>();
        for (Path directory : directories) {
            if (Files.exists(directory)) {
                try (Stream<Path> files = Files.find(directory, Integer.MAX_VALUE, isFileWithCorrectExtension)) {
                    result.addAll(files.collect(Collectors.toSet()));
                } catch (IOException e) {
                    LOGGER.error("Problem in finding files", e);
                }
            }
        }
        return result;
    }
}
