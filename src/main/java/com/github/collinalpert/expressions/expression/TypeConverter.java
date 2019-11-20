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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TypeConverter extends SimpleExpressionVisitor {
	// see http://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.1.2
	private static final Map<Class<?>, List<Class<?>>> primitiveWides;
	private static final Map<Class<?>, Class<?>> unboxers;
	private static final Map<Class<?>, Class<?>> boxers;

	static {
		Map<Class<?>, List<Class<?>>> wides = new HashMap<>();
		wides.put(Byte.TYPE, Arrays.asList(new Class<?>[]{Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE}));
		wides.put(Short.TYPE, Arrays.asList(new Class<?>[]{Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE}));
		wides.put(Integer.TYPE, Arrays.asList(new Class<?>[]{Long.TYPE, Float.TYPE, Double.TYPE}));
		wides.put(Long.TYPE, Arrays.asList(new Class<?>[]{Float.TYPE, Double.TYPE}));
		wides.put(Float.TYPE, Arrays.asList(new Class<?>[]{Double.TYPE}));

		primitiveWides = wides;

		HashMap<Class<?>, Class<?>> primitives = new HashMap<>(8);
		primitives.put(Boolean.class, Boolean.TYPE);
		primitives.put(Byte.class, Byte.TYPE);
		primitives.put(Character.class, Character.TYPE);
		primitives.put(Double.class, Double.TYPE);
		primitives.put(Float.class, Float.TYPE);
		primitives.put(Integer.class, Integer.TYPE);
		primitives.put(Long.class, Long.TYPE);
		primitives.put(Short.class, Short.TYPE);

		unboxers = primitives;

		primitives = new HashMap<>(8);
		primitives.put(Boolean.TYPE, Boolean.class);
		primitives.put(Byte.TYPE, Byte.class);
		primitives.put(Character.TYPE, Character.class);
		primitives.put(Double.TYPE, Double.class);
		primitives.put(Float.TYPE, Float.class);
		primitives.put(Integer.TYPE, Integer.class);
		primitives.put(Long.TYPE, Long.class);
		primitives.put(Short.TYPE, Short.class);

		boxers = primitives;
	}

	private final Class<?> _to;

	private TypeConverter(Class<?> to) {
		_to = to;
	}

	static Expression convert(Expression e, Class<?> to) {
		Class<?> from = e.getResultType();
		if (from == to) {
			return e;
		}

		return e.accept(new TypeConverter(to));
	}

	public static boolean isAssignable(Class<?> to, Class<?> from) {
		if (to.isPrimitive() ^ from.isPrimitive()) {
			if (to.isPrimitive()) {
				from = unboxers.get(from);
			} else {
				from = boxers.get(from);
			}

			if (from == null) {
				return false;
			}
		}

		if (to.isAssignableFrom(from)) {
			return true;
		}

		List<Class<?>> wides = primitiveWides.get(from);
		if (wides != null) {
			return wides.contains(to);
		}

		return false;

	}

	private Object convert(Class<?> from, Object value) {
		if (from == Integer.TYPE) {
			return convert((Integer) value);
		}

		return defaultConvert(value);
	}

	private Object convert(int value) {
		if (_to == Boolean.TYPE) {
			if (value == 0) {
				return Boolean.FALSE;
			}

			if (value == 1) {
				return Boolean.TRUE;
			}
		}

		return defaultConvert(value);
	}

	private Expression defaultConvert(Expression e) {
		if (isAssignable(_to, e.getResultType())) {
			return e;
		}

		return Expression.convert(e, _to);
	}

	private Object defaultConvert(Object value) {
		return _to.cast(value);
	}

	@Override
	public Expression visit(BinaryExpression e) {
		if (isAssignable(_to, e.getResultType())) {
			return e;
		}

		Expression first = e.getFirst().accept(this);
		Expression second = e.getSecond().accept(this);
		Expression op = e.getOperator();

		return Expression.condition(op, first, second);
	}

	@Override
	public Expression visit(ConstantExpression e) {
		Class<?> resultType = e.getResultType();
		if (isAssignable(_to, resultType)) {
			return e;
		}

		return Expression.constant(convert(resultType, e.getValue()), _to);
	}

	@Override
	public Expression visit(InvocationExpression e) {
		return defaultConvert(e);
	}

	@Override
	public Expression visit(LambdaExpression e) {
		return defaultConvert(e);
	}

	@Override
	public Expression visit(MemberExpression e) {
		return defaultConvert(e);
	}

	@Override
	public Expression visit(ParameterExpression e) {
		if (isAssignable(e.getResultType(), _to)) {
			return Expression.parameter(_to, e.getIndex());
		}

		return defaultConvert(e);
	}

	@Override
	public Expression visit(UnaryExpression e) {
		return defaultConvert(e);
	}
}
