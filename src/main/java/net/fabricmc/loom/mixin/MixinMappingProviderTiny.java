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

import net.fabricmc.mapping.tree.*;
import org.spongepowered.asm.obfuscation.mapping.common.MappingField;
import org.spongepowered.asm.obfuscation.mapping.common.MappingMethod;
import org.spongepowered.tools.obfuscation.mapping.common.MappingProvider;
import org.spongepowered.tools.obfuscation.mapping.fg3.MappingMethodLazy;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MixinMappingProviderTiny extends MappingProvider {
	private final String from, to;

	public MixinMappingProviderTiny(Messager messager, Filer filer, String from, String to) {
		super(messager, filer);
		this.from = from;
		this.to = to;
	}

	@Override
	public MappingMethod getMethodMapping(MappingMethod method) {
		MappingMethod mapped = getMethodMapping0(method);
		if (mapped != null)
			return mapped;
		
		if (method.getOwner() != null) {
			String newOwner = classMap.get(method.getOwner());
		
			if (newOwner != null && !newOwner.equals(method.getOwner())) {
				return new MappingMethodLazy(newOwner, method.getSimpleName(), method.getDesc(), this);
			}
		}

		return null;
	}
	
	private MappingMethod getMethodMapping0(MappingMethod method) {
		MappingMethod mapped = this.methodMap.get(method);
		if (mapped != null)
			return mapped;
		
		if (method.getOwner() == null) return null;
		
		try {
			final Class<?> c = this.loadClassOrNull(method.getOwner().replace('/', '.'));
			
			if (c != null && c != Object.class) {
				for (Class<?> cc : c.getInterfaces()) {
					mapped = getMethodMapping0(method.move(cc.getName().replace('.', '/')));
					if (mapped != null) {
						mapped = mapped.move(classMap.getOrDefault(method.getOwner(), method.getOwner()));
						methodMap.put(method, mapped);
						return mapped;
					}
				}
	
				if (c.getSuperclass() != null) {
					mapped = getMethodMapping0(method.move(c.getSuperclass().getName().replace('.', '/')));
					if (mapped != null) {
						mapped = mapped.move(classMap.getOrDefault(method.getOwner(), method.getOwner()));
						methodMap.put(method, mapped);
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
	public MappingField getFieldMapping(MappingField field) {
		// Remove any form of method parameters form the field desc, working around https://github.com/SpongePowered/Mixin/issues/419
		String desc = field.getDesc();
		int i = desc.indexOf(")");

		if (i != -1) {
			desc = desc.substring(i + 1);
		}

		MappingField fixed = new MappingField(field.getOwner(), field.getSimpleName(), desc);
		MappingField mapped = this.fieldMap.get(fixed);
		if (mapped != null)
			return mapped;
		
		if (field.getOwner() == null) return null;
		
		/* try {
			Class c = this.getClass().getClassLoader().loadClass(field.getOwner().replace('/', '.'));
			if (c == null || c == Object.class) {
				return null;
			}

			if (c.getSuperclass() != null) {
				mapped = getFieldMapping(field.move(c.getSuperclass().getName().replace('.', '/')));
				if (mapped != null)
					return mapped;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} */
		
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
}
