package com.jeevaneo.util;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for OS specific actions.
 * 
 * @author 97945n
 *
 */
public interface OS {

	/**
	 * Returns true on windows, false otherwise.
	 * 
	 * @return true on windows, false otherwise.
	 */
	boolean isWindows();

	/**
	 * Forks the given command. Standard ouput and error output are redirect to System.out and System.err.
	 * 
	 * @param command
	 * @throws IOException
	 */
	void fork(String... command) throws IOException;

	/**
	 * Forks the given command. Standard output is redirected to the given file.
	 * 
	 * @param output
	 * @param command
	 * @throws IOException
	 * @throws InterruptedException
	 */
	void fork(File output, String... command) throws IOException, InterruptedException;

	/**
	 * Set the given environment variable name with the given value. The variable is set until a fork command is completed.
	 * 
	 * @param envVarName
	 *            the variable name
	 * @param envVarVal
	 *            the variable value
	 */
	void setEnvVar(String envVarName, String envVarVal);

	/**
	 * Singleton.
	 */
	OS INSTANCE = new OSImpl();

}