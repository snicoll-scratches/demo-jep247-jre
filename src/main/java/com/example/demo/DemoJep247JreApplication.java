package com.example.demo;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DemoJep247JreApplication {

	private static final Logger logger = LoggerFactory.getLogger(DemoJep247JreApplication.class);

	public void compile(Path sourceDir, Path targetDir) throws IOException {
		List<String> options = List.of("-d", targetDir.toAbsolutePath().toString(), "--release", "17");
		logger.debug("Using compiler options:%n%s".formatted(options));
		List<Path> sourceFiles = allFiles(sourceDir);
		logger.debug("Using source files:%n%s".formatted(sourceFiles));
		JavaCompiler compiler = new EclipseCompiler();
		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
			Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
			Errors errors = new Errors();
			StringWriter out = new StringWriter();
			CompilationTask task = compiler.getTask(out, fileManager, errors, options, null, compilationUnits);
			boolean result = task.call();
			if (logger.isDebugEnabled()) {
				logger.debug("Compiler output: %n%s".formatted(out));
			}
			if (!result || errors.hasError) {
				throw new IllegalStateException("Unable to compile source");
			}
			logger.debug("Compiled classes:%n%s".formatted(allFiles(targetDir)));
		}
	}

	private List<Path> allFiles(Path directory) {
		try {
			try (Stream<Path> pathStream = Files.walk(directory)) {
				return pathStream.filter(Files::isRegularFile).toList();
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * {@link DiagnosticListener} used to collect errors.
	 */
	static class Errors implements DiagnosticListener<JavaFileObject> {

		private boolean hasError = false;

		@Override
		public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
			if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
				hasError = true;
				Exception ex = extractException(diagnostic);
				if (ex != null) {
					logger.error("Exception: ", ex);
				}
				else {
					logger.error(diagnostic.toString());
				}
			}
		}

		private Exception extractException(Diagnostic<? extends JavaFileObject> diagnostic) {
			try {
				Field exception = diagnostic.getClass().getDeclaredField("exception");
				exception.setAccessible(true);
				return (Exception) exception.get(diagnostic);
			}
			catch (Exception ex) {
				return null;
			}
		}

	}

	public static void main(String[] args) throws Exception {
		Path tempDir = Files.createTempDirectory("test");

		Path sourceDir = tempDir.resolve("source");
		Path sourceFile = sourceDir.resolve("com").resolve("example").resolve("Dummy.java");
		Files.createDirectories(sourceFile.getParent());
		Files.write(sourceFile, List.of("package com.example;", "", "class Dummy {}"));

		Path classesDir = tempDir.resolve("classes");
		Files.createDirectories(classesDir);

		new DemoJep247JreApplication().compile(sourceDir, classesDir);
	}

}
