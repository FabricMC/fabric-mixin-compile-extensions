/*
 * This file is part of fabric-mixin-compile-extensions, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.mixin;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.spongepowered.tools.obfuscation.interfaces.IMixinAnnotationProcessor;
import org.spongepowered.tools.obfuscation.service.IObfuscationService;
import org.spongepowered.tools.obfuscation.service.ObfuscationTypeDescriptor;

public class ObfuscationServiceFabric implements IObfuscationService {
	public static final String IN_MAP_FILE = "inMapFile";
	public static final String IN_MAP_EXTRA_FILES = "inMapExtraFiles";
	public static final String OUT_MAP_FILE = "outMapFile";

	private String asSuffixed(String arg, String from, String to) {
		return arg + MixinExtUtils.capitalize(from) + MixinExtUtils.capitalize(to);
	}

	private ObfuscationTypeDescriptor createObfuscationType(String from, String to) {
		return new ObfuscationTypeDescriptor(
			from + ":" + to,
			asSuffixed(ObfuscationServiceFabric.IN_MAP_FILE, from, to),
			asSuffixed(ObfuscationServiceFabric.IN_MAP_EXTRA_FILES, from, to),
			asSuffixed(ObfuscationServiceFabric.OUT_MAP_FILE, from, to),
			ObfuscationEnvironmentFabric.class
		);
	}

	private void addSupportedOptions(Set<String> options, String from, String to) {
		options.add(asSuffixed(ObfuscationServiceFabric.IN_MAP_FILE, from, to));
		options.add(asSuffixed(ObfuscationServiceFabric.IN_MAP_EXTRA_FILES, from, to));
		options.add(asSuffixed(ObfuscationServiceFabric.OUT_MAP_FILE, from, to));
	}

	@Override
	public Set<String> getSupportedOptions() {
		Set<String> options = new HashSet<>();
		addSupportedOptions(options, "official", "intermediary");
		addSupportedOptions(options, "official", "named");
		addSupportedOptions(options, "intermediary", "official");
		addSupportedOptions(options, "intermediary", "named");
		addSupportedOptions(options, "named", "official");
		addSupportedOptions(options, "named", "intermediary");
		return Collections.unmodifiableSet(options);
	}

	@Override
	public Collection<ObfuscationTypeDescriptor> getObfuscationTypes(IMixinAnnotationProcessor ap) {
		return getObfuscationTypes();
	}

	// Hook preserved for Mixin 0.7 backward compatibility
	public Collection<ObfuscationTypeDescriptor> getObfuscationTypes() {
		return Arrays.asList(
				createObfuscationType("official", "intermediary"),
				createObfuscationType("official", "named"),
				createObfuscationType("intermediary", "official"),
				createObfuscationType("intermediary", "named"),
				createObfuscationType("named", "official"),
				createObfuscationType("named", "intermediary")
		);
	}
}
