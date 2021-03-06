/*
 * Copyright TrigerSoft <kostat@trigersoft.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.collinalpert.expressions.expression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

final class SerializedLambda implements Serializable {
	private static final long serialVersionUID = 8025925345765570181L;

	public String functionalInterfaceClass;
	public String functionalInterfaceMethodName;
	public String implClass;
	public String implMethodName;
	public String implMethodSignature;
	public int implMethodKind;
	public Object[] capturedArgs;

	public static SerializedLambda extractLambda(Serializable lambda) {
		try {
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream(2048) {
				@Override
				public byte[] toByteArray() {
					return buf;
				}
			};
			try (ObjectOutputStream out = new ObjectOutputStream(byteOut)) {
				out.writeObject(lambda);
			}

			byte[] data = byteOut.toByteArray();
			try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data)) {

				@Override
				protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {

					Class<?> resolvedClass = super.resolveClass(desc);
					if (resolvedClass == java.lang.invoke.SerializedLambda.class) {
						return SerializedLambda.class;
					}

					return resolvedClass;
				}

			}) {
				return (SerializedLambda) in.readObject();
			}
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
