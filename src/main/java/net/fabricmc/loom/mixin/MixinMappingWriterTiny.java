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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;

import org.spongepowered.asm.obfuscation.mapping.IMapping;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.ObfuscationType;
import org.spongepowered.tools.obfuscation.mapping.IMappingConsumer;
import org.spongepowered.tools.obfuscation.mapping.common.MappingWriter;

/**
 * Created by asie on 10/9/16.
 */
public class MixinMappingWriterTiny extends MappingWriter {
	public MixinMappingWriterTiny(Messager messager, Filer filer) {
		super(messager, filer);
	}

	@Override
	public void write(String output, ObfuscationType type, IMappingConsumer.MappingSet<MappingField> fields,
					  IMappingConsumer.MappingSet<MappingMethod> methods) {
		if (output == null) {
			return;
		}
		String[] parts = type.getKey().split(":");
		String from = parts[0];
		String to = parts[1];

		Map<String, List<String>> classesData = new TreeMap<>();

		print(classesData, "f", fields);
		print(classesData, "m", methods);

		try (PrintWriter writer = this.openFileWriter(output, type + " output TinyMappings")) {
			writer.println(String.format("tiny\t2\t0\t%s\t%s", from, to));
			// writer.println("\tsorted-classes"); todo write sorted attribute
			for (List<String> lines : classesData.values()) {
				for (String line : lines) {
					writer.println(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void print(Map<String, List<String>> classesData, String type, IMappingConsumer.MappingSet<? extends IMapping<?>> mappingSet) {
		for (IMappingConsumer.MappingSet.Pair<? extends IMapping<?>> pair : mappingSet) {
			classesData.computeIfAbsent(pair.from.getOwner(), key -> {
				List<String> ret = new ArrayList<>();
				ret.add(makeClassLine(key, pair.to.getOwner()));
				return ret;
			}).add(makeClassMemberLine(type, pair.from.getDesc(), pair.from.getName(), pair.to.getName()));
		}
	}

	private static String makeClassLine(String oldName, String newName) {
		StringBuilder result = new StringBuilder("c\t");

		result.append(oldName).append("\t");
		if (!oldName.equals(newName)) result.append(newName);

		return result.toString();
	}

	private static String makeClassMemberLine(String type, String desc, String oldName, String newName) {
		StringBuilder result = new StringBuilder("\t").append(type).append("\t");

		result.append(desc).append("\t");
		result.append(oldName).append("\t");
		if (!oldName.equals(newName)) result.append(newName);

		return result.toString();
	}
}
