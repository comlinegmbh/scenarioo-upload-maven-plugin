package de.comline.maven.scenarioo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

class ZipWriter {
	private final Path branchDirectory;

	ZipWriter(Path branchDirectory) {
		this.branchDirectory = branchDirectory;
	}
	
	void write(OutputStream out) throws IOException {
		Path relativizeTo = branchDirectory.getParent();
		
		try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
			Files.walk(branchDirectory)
				.filter(path -> Files.isRegularFile(path))
				.forEach(path -> addToZip(path, relativizeTo, zipOut));
		}
	}

	private void addToZip(Path path, Path relativeRoot, ZipOutputStream zipOut) {
		try {
			Path relativePath = relativeRoot.relativize(path);
			// The separator for zip files seems to be the Unix path delimiter,
			// see ZipEntry#isDirectory
			String relativeUnix = relativePath.toString().replace('\\', '/');
			ZipEntry entry = new ZipEntry(relativeUnix);

			zipOut.putNextEntry(entry);
			try (InputStream in = Files.newInputStream(path)) {
				IOUtils.copy(in, zipOut);
			}
			zipOut.closeEntry();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
}
