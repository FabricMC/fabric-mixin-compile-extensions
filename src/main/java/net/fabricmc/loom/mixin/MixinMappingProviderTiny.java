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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;

import org.spongepowered.asm.obfuscation.mapping.IMapping;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.mapping.common.MappingProvider;
import org.spongepowered.tools.obfuscation.mapping.fg3.MappingMethodLazy;

import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.FieldDef;
import net.fabricmc.mapping.tree.MethodDef;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public class MixinMappingProviderTiny extends MappingProvider {
	private final String from, to;

	// Done to account for MappingProvider's maps being from guava, and shaded.
	protected final Map<String, String> classMap = getMap("classMap");
	protected final Map<MappingField, MappingField> fieldMap = getMap("fieldMap");
	protected final Map<MappingMethod, MappingMethod> methodMap = getMap("methodMap");

	public MixinMappingProviderTiny(Messager messager, Filer filer, String from, String to) {
		super(messager, filer);
		this.from = from;
		this.to = to;
	}

	@Override
	public MappingMethod getMethodMapping(MappingMethod method) {
		MappingMethod mapped = getMapping0(method, methodMap);
		if (mapped != null) return mapped;

		if (method.getOwner() != null) {
			String newOwner = classMap.get(method.getOwner());

			if (newOwner != null && !newOwner.equals(method.getOwner())) {
				return new MappingMethodLazy(newOwner, method.getSimpleName(), method.getDesc(), this);
			}
		}

		return null;
	}

	@Override
	public MappingField getFieldMapping(MappingField field) {
		// Remove any form of method parameters form the field desc, working around https://github.com/SpongePowered/Mixin/issues/419
		String desc = field.getDesc();
		int i = desc.indexOf(")");

		if (i >= 0) {
			desc = desc.substring(i + 1);
			field = new MappingField(field.getOwner(), field.getSimpleName(), desc);
		}

		MappingField mapped = getMapping0(field, fieldMap);
		if (mapped != null) return mapped;

		if (field.getOwner() != null) {
			String newOwner = classMap.get(field.getOwner());

			if (newOwner != null && !newOwner.equals(field.getOwner())) {
				String newDesc;

				if (desc.endsWith(";")) {
					int pos = desc.indexOf('L');
					assert pos >= 0;
					String cls = desc.substring(pos + 1, desc.length() - 1);
					newDesc = String.format("%s%s;", desc.substring(0, pos + 1), classMap.getOrDefault(cls, cls));
				} else {
					newDesc = desc;
				}

				return new MappingField(newOwner, field.getSimpleName(), newDesc);
			}
		}

		return null;
	}

	private <T extends IMapping<T>> T getMapping0(T member, Map<T, T> map) {
		T mapped = map.get(member);
		if (mapped != null) return mapped;

		if (member.getOwner() == null) return null;

		try {
			final Class<?> c = this.loadClassOrNull(member.getOwner().replace('/', '.'));

			if (c != null && c != Object.class) {
				for (Class<?> cc : c.getInterfaces()) {
					mapped = getMapping0(member.move(cc.getName().replace('.', '/')), map);

					if (mapped != null) {
						mapped = mapped.move(classMap.getOrDefault(member.getOwner(), member.getOwner()));
						map.put(member, mapped);
						return mapped;
					}
				}

				if (c.getSuperclass() != null) {
					mapped = getMapping0(member.move(c.getSuperclass().getName().replace('.', '/')), map);

					if (mapped != null) {
						mapped = mapped.move(classMap.getOrDefault(member.getOwner(), member.getOwner()));
						map.put(member, mapped);
						return mapped;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public void read(File input) throws IOException {
		TinyTree tree;
		try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
			tree = TinyMappingFactory.loadWithDetection(reader);
		}

		for (ClassDef cls : tree.getClasses()) {
			String fromClass = cls.getName(from);
			String toClass = cls.getName(to);
			classMap.put(fromClass, toClass);

			for (FieldDef field : cls.getFields()) {
				fieldMap.put(new MappingField(fromClass, field.getName(from), field.getDescriptor(from)), new MappingField(toClass, field.getName(to), field.getDescriptor(to)));
			}

			for (MethodDef method : cls.getMethods()) {
				methodMap.put(new MappingMethod(fromClass, method.getName(from), method.getDescriptor(from)), new MappingMethod(toClass, method.getName(to), method.getDescriptor(to)));
			}
		}
	}

	private Class<?> loadClassOrNull(final String className) {
		try {
			return this.getClass().getClassLoader().loadClass(className);
		} catch (final ClassNotFoundException ex) {
			return null;
		}
	}

	@SuppressWarnings("rawtypes")
	private Map getMap(String name) {
		try {
			Field field = MappingProvider.class.getDeclaredField(name);
			field.setAccessible(true);
			return (Map) field.get(this);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
