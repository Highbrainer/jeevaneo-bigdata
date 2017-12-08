package com.jeevaneo.util;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

public class Args {

	protected Logger log = Logger.getLogger(getClass());

	private String[] args;

	private Set<String> checkedParams = new HashSet<>();

	public Args(String... args) {
		super();
		this.args = args;
	};

	public void autocheck() {
		checkParams(checkedParams.toArray(new String[checkedParams.size()]));
	}

	public void populate(Object bean) {
		Pattern p = Pattern.compile("^--([^=]++)");
		Arrays.stream(args).forEach(arg -> {
			Matcher m = p.matcher(arg);
			if (m.find()) {
				String name = m.group(1);
				String setterName = "set" + Arrays.stream(name.split("-")).map(s -> firstUpper(s.toLowerCase())).collect(Collectors.joining());
				Arrays.stream(bean.getClass().getDeclaredMethods())
						.filter(meth -> meth.getName().equals(setterName) && meth.getParameters().length == 1).forEach(meth -> {
							Class<?> paramType = meth.getParameters()[0].getType();
							Object value = null;
							String sValue = arg.replaceFirst("^--" + name + "=?", "");
							boolean typeIsSupported = true;
							if (String.class.isAssignableFrom(paramType)) {
								value = sValue;
							} else if (int.class.isAssignableFrom(paramType) || Integer.class.isAssignableFrom(paramType)) {
								value = Integer.parseInt(sValue);
								// TODO catch NumberFormatException to give nice error message!
							} else if (float.class.isAssignableFrom(paramType) || Float.class.isAssignableFrom(paramType)) {
								value = Float.parseFloat(sValue);
								// TODO catch NumberFormatException to give nice error message!
							} else if (double.class.isAssignableFrom(paramType) || Double.class.isAssignableFrom(paramType)) {
								value = Double.parseDouble(sValue);
								// TODO catch NumberFormatException to give nice error message!
							} else if (boolean.class.isAssignableFrom(paramType) || Boolean.class.isAssignableFrom(paramType)) {
								if (sValue.trim().isEmpty()) {
									value = true;
								}
								value = Boolean.parseBoolean(sValue);
								// TODO catch NumberFormatException to give nice error message!
							} else {
								log.warn("Fields of type " + paramType + " are not supported");
								typeIsSupported = false;
							}
							if (typeIsSupported) {
								try {
									meth.invoke(bean, value);
								} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
									throw new RuntimeException("Erreur lors de la tentative d'initialiser la propriÃ©tÃ© '" + name + "' via " + meth,
											e);
								}
							}
						});
				;
			}
		});
	}

	private String firstUpper(String token) {
		if (null == token || token.isEmpty()) {
			return token;
		}
		return token.substring(0, 1).toUpperCase() + token.substring(1);
	}

	public void checkParams(String... validArgs) {
		List<String> validArgsL = Arrays.asList(validArgs);
		for (String arg : this.args) {
			if (!arg.startsWith("--") || !arg.contains("=")) {
				throw new IllegalArgumentException("ParamÃ¨tre " + arg + " doit avoir la forme --paramName=paramValue !");
			}

			if (!validArgsL.contains(arg.substring(2, arg.indexOf("=")))) {
				throw new IllegalArgumentException("ParamÃ¨tre " + arg + " non reconnu !\nParamÃ¨tres disponibles : " + validArgsL);
			}
		}
	}

	/**
	 * mandatory string with prefix --name=
	 */
	public String ms(String name) {
		String ret = os(name);
		if (null == ret) {
			throw new IllegalArgumentException("ParamÃ¨tre '--" + name + "=' obligatoire!");
		}
		return ret;
	}

	public String os(String name) {
		checkedParams.add(name);
		for (String arg : args) {
			final String prefix = "--" + name + "=";
			if (arg.startsWith(prefix)) {
				return arg.substring(prefix.length());
			}
		}
		return null;
	}

	public String os(String name, String defaultValue) {
		String val = os(name);
		return null == val ? defaultValue : val;
	}

	public int mi(String name) {
		Integer ret = oi(name);
		if (null == ret) {
			throw new IllegalArgumentException("ParamÃ¨tre '--" + name + "=' obligatoire!");
		}
		return ret;
	}

	public Integer oi(String name) {
		String val = os(name);
		if (null == val || val.trim().isEmpty()) {
			return null;
		}
		return Integer.parseInt(val);
	}

	public int oi(String name, int defaultValue) {
		Integer val = oi(name);
		return null == val ? defaultValue : val;
	}

	public boolean mb(String name) {
		Boolean ret = ob(name);
		if (null == ret) {
			throw new IllegalArgumentException("ParamÃ¨tre '--" + name + "=' obligatoire!");
		}
		return ret;
	}

	public Boolean ob(String name) {
		checkedParams.add(name);
		String simple = "--" + name;
		final String prefix = simple + "=";
		for (String arg : args) {
			if (arg.equals(simple)) {
				return true;
			}
			if (arg.startsWith(prefix)) {
				return arg.substring(prefix.length()).trim().toLowerCase().equals("true");
			}
		}
		return null;
	}

	public boolean ob(String name, boolean defaultValue) {
		Boolean val = ob(name);
		return null == val ? defaultValue : val;
	}

	public long ml(String name) {
		Long ret = ol(name);
		if (null == ret) {
			throw new IllegalArgumentException("ParamÃ¨tre '--" + name + "=' obligatoire!");
		}
		return ret;
	}

	public Long ol(String name) {
		String val = os(name);
		if (null == val || val.trim().isEmpty()) {
			return null;
		}
		return Long.parseLong(val);
	}

	public long ol(String name, long defaultValue) {
		Long val = ol(name);
		return null == val ? defaultValue : val;
	}

	public Float of(String name) {
		String val = os(name);
		if (null == val || val.trim().isEmpty()) {
			return null;
		}
		return Float.parseFloat(val);
	}

	public float of(String name, float defaultValue) {
		Float val = of(name);
		return null == val ? defaultValue : val;
	}

	public float mf(String name) {
		Float ret = of(name);
		if (null == ret) {
			throw new IllegalArgumentException("ParamÃ¨tre '--" + name + "=' obligatoire!");
		}
		return ret;
	}

	public Double od(String name) {
		String val = os(name);
		if (null == val || val.trim().isEmpty()) {
			return null;
		}
		return Double.parseDouble(val);
	}

	public double od(String name, double defaultValue) {
		Double val = od(name);
		return null == val ? defaultValue : val;
	}

	public double md(String name) {
		Double ret = od(name);
		if (null == ret) {
			throw new IllegalArgumentException("ParamÃ¨tre '--" + name + "=' obligatoire!");
		}
		return ret;
	}
}
