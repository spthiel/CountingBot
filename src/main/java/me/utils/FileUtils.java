package me.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class FileUtils {
	
	public static String readFile(Path path) {
		
		var file = path.toFile();
		
		try (var bufferedReader = new BufferedReader(new FileReader(file))) {
			StringBuilder content = new StringBuilder();
			
			String line = "";
			while ((line = bufferedReader.readLine()) != null) {
				content.append(line);
			}
			
			return content.toString();
			
		} catch (IOException ignored) {}
		
		return "";
	}
	
	public static Path getJarPath() {
		
		try {
			return Path.of(FileUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException e) {
			// Should never happen
			throw new RuntimeException(e);
		}
	}
	
}
