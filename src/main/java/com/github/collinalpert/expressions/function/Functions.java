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

package com.github.collinalpert.expressions.function;

import com.github.collinalpert.expressions.function.math.BinaryOperator;
import com.github.collinalpert.expressions.function.math.UnaryOperator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Contains static factory methods to create composed functions.
 */
public final class Functions {
	private Functions() {
	}

	/**
	 * Returns the absolute value of an argument evaluation result. In other words, the result is semantically equivalent to
	 * this pseudo-code: {@code abs(selector.apply(N))}
	 *
	 * @param <T>      the type of the input type.
	 * @param selector a function to extract the numeric value from T.
	 * @return the absolute value of an argument evaluation result.
	 */
	public static <T> Function<T, Number> abs(Function<T, ? extends Number> selector) {
		return selector.andThen(UnaryOperator.Abs::eval);
	}

	/**
	 * Returns the absolute value of an argument.
	 *
	 * @param <N> the type of the argument.
	 * @return the absolute value of an argument.
	 */
	public static <N extends Number> Function<N, Number> abs() {
		return abs(Function.<N>identity());
	}

	/**
	 * Returns the negative value of an argument evaluation result. In other words, the result is semantically equivalent to
	 * this pseudo-code: {@code -selector.invoke(?)}
	 *
	 * @param selector a function to negate the numeric parameter.
	 * @return the negative value of an argument evaluation result.
	 */
	public static Function<?, Number> negate(Function<?, ? extends Number> selector) {
		return selector.andThen(UnaryOperator.Negate::eval);
	}

	/**
	 * Returns the negative value of an argument.
	 *
	 * @return the negative value of an argument.
	 */
	public static Function<?, Number> negate() {
		return negate(Function.identity());
	}

	/**
	 * Returns the bitwise not value of an argument evaluation result. In other words, the result is semantically equivalent
	 * to this pseudo-code: {@code ~selector.invoke(?)}
	 *
	 * @param selector a function to complement the numeric parameter.
	 * @return the bitwise not value of an argument evaluation result.
	 */
	public static Function<?, Number> bitwiseNot(Function<?, ? extends Number> selector) {
		return selector.andThen(UnaryOperator.Not::eval);
	}

	/**
	 * Returns the bitwise not value of an argument.
	 *
	 * @return the bitwise not value of an argument.
	 */
	public static Function<?, Number> bitwiseNot() {
		return bitwiseNot(Function.identity());
	}

	/**
	 * Returns the value of {@code left.apply(T) & right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) & right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> bitwiseAnd(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.And.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) + right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) + right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> add(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Add.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) - right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) - right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> subtract(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Subtract.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) * right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) * right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> multiply(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Multiply.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) / right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) / right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> divide(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Divide.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) % right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) % right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> modulo(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Modulo.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) | right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) | right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> bitwiseOr(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Or.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of <code>left.apply(T)<sup>right.apply(U)</sup></code>.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of <code>left.apply(T)<sup>right.apply(U)</sup></code>
	 */
	public static <T, U> BiFunction<T, U, Number> power(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Power.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) << right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) << right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> shiftLeft(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.ShiftLeft.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) >> right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) >> right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> shiftRight(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.ShiftRight.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) ^ right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) ^ right.apply(U)}
	 */
	public static <T, U> BiFunction<T, U, Number> xor(Function<T, ? extends Number> left, Function<U, ? extends Number> right) {
		return (T t, U u) -> BinaryOperator.Xor.eval(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) && right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) && right.apply(U)}
	 */
	public static <T, U> BiPredicate<T, U> and(Predicate<T> left, Predicate<U> right) {
		return (T t, U u) -> left.test(t) && right.test(u);
	}

	/**
	 * Returns the value of {@code left.apply(T) && right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) && right.apply(U)}
	 */
	public static <T, U> BiPredicate<T, U> and(Function<T, Boolean> left, Function<U, Boolean> right) {
		return and((Predicate<T>) left::apply, (Predicate<U>) right::apply);
	}

	/**
	 * Returns the value of {@code left.apply(T) || right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) || right.apply(U)}
	 */
	public static <T, U> BiPredicate<T, U> or(Predicate<T> left, Predicate<U> right) {
		return (T t, U u) -> left.test(t) || right.test(u);
	}

	/**
	 * Returns the value of {@code left.apply(T) || right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) || right.apply(U)}
	 */
	public static <T, U> BiPredicate<T, U> or(Function<T, Boolean> left, Function<U, Boolean> right) {
		return or((Predicate<T>) left::apply, (Predicate<U>) right::apply);
	}

	/**
	 * Returns the value of {@code left.apply(T) < right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param <Key> the type of the value to compare on.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) < right.apply(U)}
	 */
	public static <T, U, Key extends Comparable<Key>> BiPredicate<T, U> lessThan(Function<T, Key> left, Function<U, Key> right) {
		return (T t, U u) -> {
			Key k1 = left.apply(t);
			Key k2 = right.apply(u);
			return (k1 instanceof Number && k2 instanceof Number)
					? Objects.compare(k1, k2, (Key key1, Key key2) -> BinaryOperator.Subtract.eval((Number) key1, (Number) key2).intValue()) < 0
					: k1.compareTo(k2) < 0;
		};
	}

	/**
	 * Returns the value of {@code left.apply(T) <= right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param <Key> the type of the value to compare on.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) <= right.apply(U)}
	 */
	public static <T, U, Key extends Comparable<Key>> BiPredicate<T, U> lessThanOrEqual(Function<T, Key> left, Function<U, Key> right) {
		return (T t, U u) -> {
			Key k1 = left.apply(t);
			Key k2 = right.apply(u);
			return (k1 instanceof Number && k2 instanceof Number)
					? Objects.compare(k1, k2, (Key key1, Key key2) -> BinaryOperator.Subtract.eval((Number) key1, (Number) key2).intValue()) <= 0
					: k1.compareTo(k2) <= 0;
		};
	}


	/**
	 * Returns the value of {@code left.apply(T) == right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) == right.apply(U)}
	 */
	public static <T, U> BiPredicate<T, U> equal(Function<T, ?> left, Function<U, ?> right) {
		return (T t, U u) -> Objects.equals(left.apply(t), right.apply(u));
	}

	/**
	 * Returns the value of {@code left.apply(T) >= right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param <Key> the type of the value to compare on.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) >= right.apply(U)}
	 */
	public static <T, U, Key extends Comparable<Key>> BiPredicate<T, U> greaterThanOrEqual(Function<T, Key> left, Function<U, Key> right) {
		return (T t, U u) -> {
			Key k1 = left.apply(t);
			Key k2 = right.apply(u);
			return (k1 instanceof Number && k2 instanceof Number)
					? Objects.compare(k1, k2, (Key key1, Key key2) -> BinaryOperator.Subtract.eval((Number) key1, (Number) key2).intValue()) >= 0
					: k1.compareTo(k2) >= 0;
		};
	}

	/**
	 * Returns the value of {@code left.apply(T) > right.apply(U)}.
	 *
	 * @param <T>   the type of the first argument.
	 * @param <U>   the type of the second argument.
	 * @param <Key> the type of the value to compare on.
	 * @param left  a function to extract the left hand value from T.
	 * @param right a function to extract the right hand value from T.
	 * @return the value of {@code left.apply(T) > right.apply(U)}
	 */
	public static <T, U, Key extends Comparable<Key>> BiPredicate<T, U> greaterThan(Function<T, Key> left, Function<U, Key> right) {
		return (T t, U u) -> {
			Key k1 = left.apply(t);
			Key k2 = right.apply(u);
			return (k1 instanceof Number && k2 instanceof Number)
					? Objects.compare(k1, k2, (Key key1, Key key2) -> BinaryOperator.Subtract.eval((Number) key1, (Number) key2).intValue()) > 0
					: k1.compareTo(k2) > 0;
		};
	}

	/**
	 * Represents a function returning a constant value.
	 *
	 * @param <Result> the type of returned value.
	 * @param <T>      the type of the ignored argument.
	 * @param result   value to return.
	 * @return result.
	 */
	public static <T, Result> Function<T, Result> constant(final Result result) {
		return (T t) -> result;
	}

	/**
	 * Represents a function returning value of a property using reflection.
	 *
	 * @param <Result>     the type of returned value.
	 * @param <T>          the type of the argument.
	 * @param outerType    Class of the objects in the sequence.
	 * @param propertyName name of the property to retrieve. The implementation will look for get&lt;propertyName&gt;() method.
	 * @return value of the property.
	 * @throws NoSuchMethodException if a matching method is not found.
	 */
	@SuppressWarnings("unchecked")
	public static <T, Result> Function<T, Result> property(Class<? super T> outerType, String propertyName) throws NoSuchMethodException {
		Method d = outerType.getDeclaredMethod("get" + propertyName);
		return (T t) -> {
			try {
				return (Result) d.invoke(t);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		};
	}

	/**
	 * Represents a function returning value of a field using reflection.
	 *
	 * @param <Result>  the type of returned value.
	 * @param <T>       the type of the argument.
	 * @param outerType Class of the objects in the sequence.
	 * @param fieldName name of the field to retrieve.
	 * @return value of the field.
	 * @throws NoSuchFieldException if a field with the specified name is not found.
	 */
	@SuppressWarnings("unchecked")
	public static <T, Result> Function<T, Result> field(Class<? super T> outerType, String fieldName) throws NoSuchFieldException {
		java.lang.reflect.Field d = outerType.getDeclaredField(fieldName);
		return (T t) -> {
			try {
				return (Result) d.get(t);
			} catch (IllegalAccessException | IllegalArgumentException e) {
				throw new RuntimeException(e);
			}
		};
	}

	/**
	 * Returns the value of {@code operand.apply(T) instanceof type}.
	 *
	 * @param <Result> the type of returned value.
	 * @param <T>      the type of the argument.
	 * @param operand  a function to extract the operand from T
	 * @param type     the Class to test by.
	 * @return the value of {@code operand.apply(T) instanceof type}.
	 */
	public static <Result, T> Predicate<T> instanceOf(final Function<? super T, ? extends Result> operand, final Class<?> type) {
		return t -> type.isInstance(operand.apply(t));
	}

	/**
	 * Returns the value of {@code predicate.apply(T) ? ifTrue.apply(T) : ifFalse.apply(T)}.
	 * <p>
	 * Note, that either ifTrue or ifFalse function is evaluated.
	 * </p>
	 *
	 * @param <Result>  the type of returned value.
	 * @param <T>       the type of the argument.
	 * @param predicate predicate to test.
	 * @param ifTrue    a function to evaluate if predicate returns true.
	 * @param ifFalse   a function to evaluate if predicate returns false.
	 * @return the value of {@code predicate.apply(T) ? ifTrue.apply(T) : ifFalse.apply(T)}.
	 */
	public static <Result, T> Function<T, Result> iif(final Function<? super T, Boolean> predicate, final Function<? super T, ? extends Result> ifTrue,
													  final Function<? super T, ? extends Result> ifFalse) {
		return t -> predicate.apply(t) ? ifTrue.apply(t) : ifFalse.apply(t);
	}

	/**
	 * Negates the return value of a predicate. In other words: {@code !predicate.apply(T)}.
	 *
	 * @param <T>       the type of the argument.
	 * @param predicate predicate to negate its evaluation result.
	 * @return value of {@code !predicate.apply(T)}.
	 */
	public static <T> Predicate<T> not(Function<T, Boolean> predicate) {
		Predicate<T> p = predicate::apply;
		return p.negate();
	}
}
