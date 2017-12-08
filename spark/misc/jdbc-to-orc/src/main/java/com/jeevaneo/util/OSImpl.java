package com.jeevaneo.util;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

/**
 * ImplÃ©mentation de OS.
 * 
 * @author 97945n
 *
 */
public class OSImpl implements OS {

	protected Logger log = Logger.getLogger(getClass());
	private Map<String, String> envVars = new HashMap<>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.jeevaneo.util.OS#isWindows()
	 */
	@Override
	public boolean isWindows() {
		final String os = System.getProperty("os.name", "unknown");
		return os.toLowerCase().contains("windows");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.jeevaneo.util.OS#fork(java.lang.String)
	 */
	@Override
	public void fork(String... command) throws IOException {
		fork(null, command);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.jeevaneo.util.OS#fork(java.io.File, java.lang.String)
	 */
	@Override
	public void fork(File output, String... command) throws IOException {
		ProcessBuilder pb = new ProcessBuilder(command);

		Map<String, String> env = pb.environment();
		env.putAll(this.envVars);

		if (null != output) {
			pb.redirectOutput(Redirect.to(output));
		} else {
			pb.redirectOutput(Redirect.INHERIT);
		}
		pb.redirectError(Redirect.INHERIT);
		Process p = pb.start();
		int exit;
		try {
			exit = p.waitFor();
		} catch (InterruptedException e) {
			throw new IOException(e);
		} finally {
			this.envVars = new HashMap<>();
		}
		if (exit != 0) {
			throw new RuntimeException("Command returned " + exit + " : " + Arrays.stream(command).collect(Collectors.joining(" ")));
		} else {
			log.info("Command OK : " + Arrays.stream(command).collect(Collectors.joining(" ")));
		}
	}

	@Override
	public void setEnvVar(String envVarName, String envVarVal) {
		envVars.put(envVarName, envVarVal);
	}
}
