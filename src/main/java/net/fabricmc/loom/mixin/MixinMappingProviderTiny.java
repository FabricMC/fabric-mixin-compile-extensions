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

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.obfuscation.mapping.IMapping;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.mapping.common.MappingProvider;
import org.spongepowered.tools.obfuscation.mapping.fg3.MappingMethodLazy;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.*;
import java.lang.reflect.Field;
import java.util.Map;

public class MixinMappingProviderTiny extends MappingProvider {
	private final String from, to;

	// Done to account for MappingProvider's maps being from guava, and shaded.
	protected final Map<String, String> classMap = getMap("classMap");
	protected final Map<MappingField, MappingField> fieldMap = getMap("fieldMap");
	protected final Map<MappingMethod, MappingMethod> methodMap = getMap("methodMap");

	private final ClassLoader classLoader = getClass().getClassLoader();

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
			String newOwner = classMap.getOrDefault(method.getOwner(), method.getOwner());
			String newDesc = new MappingMethodLazy(newOwner, method.getSimpleName(), method.getDesc(), this).getDesc();

			if (!newOwner.equals(method.getOwner()) || !newDesc.equals(method.getDesc())) {
				return new MappingMethod(newOwner, method.getSimpleName(), newDesc);
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
			String newOwner = classMap.getOrDefault(field.getOwner(), field.getOwner());
			String newDesc;

			if (desc.endsWith(";")) {
				int pos = desc.indexOf('L');
				assert pos >= 0;
				String cls = desc.substring(pos + 1, desc.length() - 1);
				newDesc = String.format("%s%s;", desc.substring(0, pos + 1), classMap.getOrDefault(cls, cls));
			} else {
				newDesc = desc;
			}

			if (!newOwner.equals(field.getOwner()) || !newDesc.equals(field.getDesc())) {
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
			final ClassNode c = this.loadClassOrNull(member.getOwner());

			if (c == null) {
				return null;
			}

			if ("java/lang/Object".equals(c.name)) {
				return null;
			}

			for (String iface : c.interfaces) {
				mapped = getMapping0(member.move(iface), map);

				if (mapped != null) {
					mapped = mapped.move(classMap.getOrDefault(member.getOwner(), member.getOwner()));
					map.put(member, mapped);
					return mapped;
				}
			}

			if (c.superName != null) {
				mapped = getMapping0(member.move(c.superName), map);

				if (mapped != null) {
					mapped = mapped.move(classMap.getOrDefault(member.getOwner(), member.getOwner()));
					map.put(member, mapped);
					return mapped;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	@Override
	public void read(File input) throws IOException {
		MemoryMappingTree tree = new MemoryMappingTree();

		try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
			MappingReader.read(reader, tree);
		}

		final int fromId = tree.getNamespaceId(from);
		final int toId = tree.getNamespaceId(to);

		for (MappingTree.ClassMapping cls : tree.getClasses()) {
			String fromClass = cls.getName(fromId);
			String toClass = cls.getName(to);
			classMap.put(fromClass, toClass);

			for (MappingTree.FieldMapping field : cls.getFields()) {
				fieldMap.put(new MappingField(fromClass, field.getName(fromId), field.getDesc(fromId)), new MappingField(toClass, field.getName(toId), field.getDesc(toId)));
			}

			for (MappingTree.MethodMapping method : cls.getMethods()) {
				methodMap.put(new MappingMethod(fromClass, method.getName(fromId), method.getDesc(fromId)), new MappingMethod(toClass, method.getName(toId), method.getDesc(toId)));
			}
		}
	}

	private ClassNode loadClassOrNull(final String className) {
		String classFileName = getClassFileName(className);

		try (InputStream is = classLoader.getResourceAsStream(classFileName)) {
			if (is == null) {
				return null;
			}

			final ClassNode classNode = new ClassNode();
			new ClassReader(is).accept(classNode, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			return classNode;
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read class" + className, e);
		}
	}

	public String getClassFileName(String className) {
		return className.replace('.', '/').concat(".class");
	}

	@SuppressWarnings("unchecked")
	private <K, V> Map<K, V> getMap(String name) {
		try {
			Field field = MappingProvider.class.getDeclaredField(name);
			field.setAccessible(true);
			return (Map<K, V>) field.get(this);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
