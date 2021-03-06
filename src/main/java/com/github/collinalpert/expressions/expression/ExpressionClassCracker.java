/*
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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

class ExpressionClassCracker {

	private static final String DUMP_FOLDER_SYSTEM_PROPERTY = "jdk.internal.lambda.dumpProxyClasses";
	private static final URLClassLoader lambdaClassLoader;
	private static final String lambdaClassLoaderCreationError;

	private static ExpressionClassCracker instance = new ExpressionClassCracker();

	static {
		String folderPath = System.getProperty(DUMP_FOLDER_SYSTEM_PROPERTY);
		if (folderPath == null) {
			lambdaClassLoaderCreationError = "Ensure that the '" + DUMP_FOLDER_SYSTEM_PROPERTY + "' system property is properly set.";
			lambdaClassLoader = null;
		} else {
			File folder = new File(folderPath);
			if (!folder.isDirectory()) {
				lambdaClassLoaderCreationError = "Ensure that the '" + DUMP_FOLDER_SYSTEM_PROPERTY + "' system property is properly set (" + folderPath
						+ " does not exist).";
				lambdaClassLoader = null;
			} else {
				URL folderURL;
				try {
					folderURL = folder.toURI().toURL();
				} catch (MalformedURLException mue) {
					throw new RuntimeException(mue);
				}

				lambdaClassLoaderCreationError = null;
				lambdaClassLoader = new URLClassLoader(new URL[]{folderURL});
			}
		}
	}

	private ExpressionClassCracker() {
	}

	public static ExpressionClassCracker getInstance() {
		return instance;
	}

	LambdaExpression lambda(Object lambda) {
		Class<?> lambdaClass = lambda.getClass();
		if (!lambdaClass.isSynthetic()) {
			throw new IllegalArgumentException("The requested object is not a Java lambda");
		}

		if (lambda instanceof Serializable) {
			SerializedLambda extracted = SerializedLambda.extractLambda((Serializable) lambda);

			ClassLoader lambdaClassLoader = lambdaClass.getClassLoader();
			return lambda(extracted, lambdaClassLoader);
		}

		return lambdaFromFileSystem(lambda, null);
	}

	LambdaExpression lambdaFromFileSystem(Object lambda, Method lambdaMethod) {
		ExpressionClassVisitor lambdaVisitor = parseFromFileSystem(lambda, lambdaMethod);

		return createLambda(lambdaVisitor);
	}

	LambdaExpression lambdaFromClassLoader(ClassLoader classLoader, String className, Supplier<ConstantExpression> instance, String method,
										   String methodDescriptor) {
		ExpressionClassVisitor lambdaVisitor = parseClass(classLoader, className, instance, method, methodDescriptor);

		return createLambda(lambdaVisitor);
	}

	private LambdaExpression createLambda(ExpressionClassVisitor lambdaVisitor) {
		Expression lambdaExpression = lambdaVisitor.getResult();
		Class<?> lambdaType = lambdaVisitor.getType();
		List<ParameterExpression> lambdaParams = Arrays.asList(lambdaVisitor.getParams());

		Expression stripped = stripConvertExpressions(lambdaExpression);

		if (stripped instanceof InvocationExpression) {
			InvocationExpression invocation = (InvocationExpression) stripped;
			InvocableExpression target = invocation.getTarget();
			if (target instanceof LambdaExpression) {
				REDUCE_CHECK:
				while (true) {
					if (!lambdaType.isAssignableFrom(target.getResultType())) {
						break;
					}

					List<Expression> args = invocation.getArguments();
					int psize = lambdaParams.size();
					if (psize != args.size()) {
						break;
					}
					for (int i = 0; i < psize; i++) {
						Expression arg = args.get(i);
						if (!(arg instanceof ParameterExpression)) {
							break REDUCE_CHECK;
						}
						ParameterExpression parg = (ParameterExpression) arg;
						ParameterExpression param = lambdaParams.get(i);
						if (parg.getIndex() != param.getIndex()) {
							break REDUCE_CHECK;
						}
						if (!param.getResultType().isAssignableFrom(parg.getResultType())) {
							break REDUCE_CHECK;
						}
					}

					return (LambdaExpression) target;
				}
			}
		}

		Expression actualExpression = TypeConverter.convert(lambdaExpression, lambdaType);
		return Expression.lambda(lambdaType, actualExpression, lambdaParams);
	}

	LambdaExpression lambda(SerializedLambda extracted, ClassLoader lambdaClassLoader) {
		boolean hasThis = extracted.implMethodKind == MethodHandleInfo.REF_invokeInterface || extracted.implMethodKind == MethodHandleInfo.REF_invokeSpecial
				|| extracted.implMethodKind == MethodHandleInfo.REF_invokeVirtual;
		boolean hasCapturedArgs = extracted.capturedArgs != null && extracted.capturedArgs.length > 0;

		ExpressionClassVisitor actualVisitor = parseClass(lambdaClassLoader, extracted.implClass, hasThis ? () -> {
			Object instance = extracted.capturedArgs[0];
			return Expression.constant(instance);
		} : null, extracted.implMethodName, extracted.implMethodSignature);

		Expression reducedExpression = TypeConverter.convert(actualVisitor.getResult(), actualVisitor.getType());

		ParameterExpression[] params = actualVisitor.getParams();

		var extractedLambda = Expression.lambda(actualVisitor.getType(), reducedExpression, List.of(params));

		if (!hasCapturedArgs || (hasThis && extracted.capturedArgs.length == 1)) {
			return extractedLambda;
		}

		List<Expression> args = new ArrayList<>(params.length);

		int capturedLength = extracted.capturedArgs.length;
		for (int i = hasThis ? 1 : 0; i < capturedLength; i++) {
			Object arg = extracted.capturedArgs[i];
			if (arg instanceof SerializedLambda) {
				SerializedLambda argLambda = (SerializedLambda) arg;

				var argExtractedLambda = lambda(argLambda, lambdaClassLoader);

				extractedLambda = (LambdaExpression) extractedLambda.accept(new ParameterReplacer(args.size(), null));

				arg = argExtractedLambda;
			}
			args.add(Expression.constant(arg));
		}

		List<ParameterExpression> finalParams = new ArrayList<>(params.length - capturedLength);
		int boundArgs = args.size();
		for (int y = boundArgs; y < params.length; y++) {
			ParameterExpression param = params[y];
			ParameterExpression arg = Expression.parameter(param.getResultType(), y - boundArgs);
			args.add(arg);
			finalParams.add(arg);
		}

		InvocationExpression newTarget = Expression.invoke(extractedLambda, args);

		return Expression.lambda(actualVisitor.getType(), newTarget, Collections.unmodifiableList(finalParams));
	}

	@SuppressWarnings("unchecked")
	<T extends Expression> T parseSyntheticArguments(T expression, List<Expression> arguments) {

		for (int i = 0; i < arguments.size(); i++) {
			Expression e = arguments.get(i);
			if (e.getExpressionType() == ExpressionType.Constant) {
				Object value = ((ConstantExpression) e).getValue();
				if (value != null && value.getClass().isSynthetic()) {
					ParameterReplacer replacer = new ParameterReplacer(i, value);
					expression = (T) expression.accept(replacer);
					if (replacer.getParsedLambda() != null) {
						arguments.set(i, Expression.constant(replacer.getParsedLambda()));
					}
				}
			}
		}
		return expression;
	}

	private ExpressionClassVisitor parseFromFileSystem(Object lambda, Method lambdaMethod) {
		if (lambdaClassLoader == null) {
			throw new RuntimeException(lambdaClassLoaderCreationError);
		}

		Class<? extends Object> lambdaClass;

		if (lambdaMethod == null) {
			lambdaClass = lambda.getClass();
			lambdaMethod = findFunctionalMethod(lambdaClass);
		} else {
			lambdaClass = lambdaMethod.getDeclaringClass();
		}
		String lambdaClassName = lambdaClassName(lambdaClass);
		return parseClass(lambdaClassLoader, lambdaClassName, () -> Expression.constant(lambda), lambdaMethod);
	}

	private String lambdaClassName(Class<?> lambdaClass) {
		String lambdaClassName = lambdaClass.getName();
		int lastIndexOfSlash = lambdaClassName.lastIndexOf('/');
		return lastIndexOfSlash > 0 ? lambdaClassName.substring(0, lastIndexOfSlash) : lambdaClassName;
	}

	private String classFilePath(String className) {
		return className.replace('.', '/') + ".class";
	}

	private Method findFunctionalMethod(Class<?> functionalClass) {
		for (Method m : functionalClass.getMethods()) {
			if (!m.isDefault()) {
				return m;
			}
		}
		throw new IllegalArgumentException("Not a lambda expression. No non-default method.");
	}

	private ExpressionClassVisitor parseClass(ClassLoader classLoader, String className, Supplier<ConstantExpression> instance, Method method) {
		return parseClass(classLoader, className, instance, method.getName(), Type.getMethodDescriptor(method));
	}

	private ExpressionClassVisitor parseClass(ClassLoader classLoader, String className, Supplier<ConstantExpression> instance, String method,
											  String methodDescriptor) {
		String classFilePath = classFilePath(className);
		ExpressionClassVisitor visitor = new ExpressionClassVisitor(classLoader, instance, method, methodDescriptor);
		try {
			try (InputStream classStream = getResourceAsStream(classLoader, classFilePath)) {
				ClassReader reader = new ClassReader(classStream);
				reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				return visitor;
			}
		} catch (IOException e) {
			throw new RuntimeException("error parsing class file " + classFilePath, e);
		}
	}

	private InputStream getResourceAsStream(ClassLoader classLoader, String path) throws FileNotFoundException {
		InputStream stream = classLoader.getResourceAsStream(path);
		if (stream == null) {
			throw new FileNotFoundException(path);
		}
		return stream;
	}

	private Expression stripConvertExpressions(Expression expression) {
		while (expression.getExpressionType() == ExpressionType.Convert) {
			expression = ((UnaryExpression) expression).getFirst();
		}
		return expression;
	}

	private static final class ParameterReplacer extends SimpleExpressionVisitor {
		private final Object lambda;
		private List<Integer> paramIndices;
		private LambdaExpression parsedLambda;

		public ParameterReplacer(int paramIndex, Object lambda) {
			this.paramIndices = Collections.singletonList(paramIndex);
			this.lambda = lambda;
		}

		public LambdaExpression getParsedLambda() {
			return parsedLambda;
		}

		@Override
		public Expression visit(InvocationExpression e) {
			if (this.paramIndices.isEmpty()) {
				return e;
			}
			List<Integer> paramIndices = this.paramIndices;
			try {

				InvocableExpression target = e.getTarget();
				Expression expr = null;
				if (target instanceof MemberExpression) {
					expr = target.accept(this);
				}
				List<Expression> args = visitArguments(e.getArguments());
				if (expr == null) {
					expr = target.accept(this);
				}
				if (args != e.getArguments() || expr != e.getTarget()) {
					return Expression.invoke((InvocableExpression) expr, args);
				}
				return e;

			} finally {
				this.paramIndices = paramIndices;
			}
		}

		@Override
		protected List<Expression> visitArguments(List<Expression> original) {
			try {
				return super.visitArguments(original);
			} finally {
				List<Integer> paramIndices = this.paramIndices;
				List<Integer> newParamIndices = new ArrayList<>();
				for (int i = 0; i < original.size(); i++) {
					Expression e = original.get(i);
					if (e.getExpressionType() == ExpressionType.Parameter) {
						ParameterExpression p = (ParameterExpression) e;
						if (paramIndices.contains(p.getIndex())) {
							newParamIndices.add(i);
						}
					}
				}

				this.paramIndices = newParamIndices;
			}
		}

		@Override
		public Expression visit(MemberExpression e) {
			Expression instance = e.getInstance();
			if (instance.getExpressionType() == ExpressionType.Parameter) {
				int index = ((ParameterExpression) instance).getIndex();
				if (paramIndices.contains(index)) {
					if (lambda != null && parsedLambda == null) {
						Method method = (Method) e.getMember();
						try {
							method = lambda.getClass().getDeclaredMethod(method.getName(), method.getParameterTypes());
						} catch (NoSuchMethodException nsme) {
							// should never happen
							throw new RuntimeException(nsme);
						}
						parsedLambda = ExpressionClassCracker.getInstance().lambdaFromFileSystem(lambda, method);
					}
					return Expression.delegate(e.getResultType(), Expression.parameter(LambdaExpression.class, index), e.getParameters());
				}
			}
			return super.visit(e);
		}

	}
}
