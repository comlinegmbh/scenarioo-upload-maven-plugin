package de.comline.maven.scenarioo;

import static java.util.stream.Collectors.toList;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "upload-report", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.DEPLOY)
public class ScenariooUploadMojo extends AbstractMojo {
	@Parameter(required = true)
	private File reportDirectory;

	@Parameter(defaultValue = "false")
	private boolean createZipFile;

	@Parameter(defaultValue = "${project.build.directory}/scenarioo-report.zip")
	private File reportZipFile;

	@Parameter(defaultValue = "http://localhost:8080/scenarioo")
	private String scenariooServerUrl;

	@Parameter(defaultValue = "scenarioo")
	private String user;

	@Parameter(required = true)
	private String password;

	private Exception exceptionInWriterThread;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			if (createZipFile) {
				createAndUploadZipFile();
			} else {
				uploadReportDirectory();
			}
		} catch (Exception ex) {
			throw new MojoExecutionException("Error while packing and uploading the scenarioo report", ex);
		}
	}

	private void createAndUploadZipFile() throws Exception {
		createReportZipFile();
		uploadReportZipFile();
	}

	private void createReportZipFile() throws MojoExecutionException, IOException {
		deleteReportZipFileIfExists();
		Path branchDirectory = findBranchDirectory();
		createZip(branchDirectory);
	}

	private void deleteReportZipFileIfExists() throws IOException {
		if (reportZipFile.exists()) {
			Path path = reportZipFile.toPath();
			Files.delete(path);
		}
	}

	private void createZip(Path branchDirectory) throws IOException {
		try (OutputStream fileOut = new FileOutputStream(reportZipFile); OutputStream bufOut = new BufferedOutputStream(fileOut)) {
			new ZipWriter(branchDirectory).write(bufOut);
		}
	}

	private void uploadReportZipFile() throws Exception {
		MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
		entityBuilder.addBinaryBody("file", reportZipFile);
		HttpEntity entity = entityBuilder.build();
		upload(entity);
	}

	private void uploadReportDirectory() throws Exception {
		// the stream will be closed from the write thread when it's done writing
		PipedOutputStream pipeOut = new PipedOutputStream();
		startWriterThread(pipeOut);

		try (InputStream pipeIn = new PipedInputStream(pipeOut)) {
			MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
			entityBuilder.addBinaryBody("file", pipeIn, ContentType.DEFAULT_BINARY, "scenarioo-report.zip");
			HttpEntity entity = entityBuilder.build();

			upload(entity);
		}
	}

	private void startWriterThread(OutputStream output) {
		Thread thread = new Thread(() -> createZipOnTheFly(output));
		thread.start();
	}

	private void createZipOnTheFly(OutputStream output) {
		try {
			Path branchDirectory = findBranchDirectory();
			new ZipWriter(branchDirectory).write(output);
			output.flush();
			output.close();
		} catch (IOException | MojoExecutionException ex) {
			exceptionInWriterThread = ex;
		}
	}

	private void upload(HttpEntity entity) throws Exception {
		URI uri = buildScenariooUri();
		getLog().debug("Will report to Scenarioo URL " + uri);

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpPost post = new HttpPost(uri);
			post.setEntity(entity);

			HttpClientContext context = createClientContext(uri);
			try (CloseableHttpResponse response = httpClient.execute(post, context)) {
				try (InputStream in = response.getEntity().getContent()) {
					String responseContent = IOUtils.toString(in, StandardCharsets.UTF_8);

					// The request *has to* be finished if the response was fully consumed
					if (exceptionInWriterThread != null) {
						throw exceptionInWriterThread;
					}

					getLog().info("Upload response: " + response.toString() + " - " + responseContent);
				}
			}
		}
	}

	private URI buildScenariooUri() {
		String uri = scenariooServerUrl;
		if (uri.endsWith("/")) {
			int end = uri.length() - 1;
			uri = uri.substring(0, end);
		}
		uri += "/rest/builds";
		return URI.create(uri);
	}

	private HttpClientContext createClientContext(URI uri) {
		Credentials credentials = new UsernamePasswordCredentials(user, password.toCharArray());
		BasicScheme scheme = new BasicScheme();
		scheme.initPreemptive(credentials);

		HttpHost host = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());

		HttpClientContext context = HttpClientContext.create();
		context.resetAuthExchange(host, scheme);
		return context;
	}

	private Path findBranchDirectory() throws MojoExecutionException, IOException {
		Path path = reportDirectory.toPath();
		List<Path> entriesInReportDirectory = Files.list(path).collect(toList());
		if (entriesInReportDirectory.size() != 1) {
			throw new MojoExecutionException("Scenarioo Report Directory " + reportDirectory.getAbsolutePath() + " contains more than one branch directory");
		}

		path = entriesInReportDirectory.get(0);
		if (Files.isDirectory(path) == false) {
			throw new MojoExecutionException("Branch directory candidate " + path.toAbsolutePath() + " is not a directory");
		}

		return path;
	}
}
