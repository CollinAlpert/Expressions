# Expressions
**Note:** You need Java 11 or a higher version installed to use this library.

This library enables Java 8 Lambdas to be represented as objects in the form of expression trees at runtime:

```java
void method(Predicate<Customer> p) {
  LambdaExpression<Predicate<Customer>> parsed = LambdaExpression.parse(p);
  //Use parsed Expression Tree...
}
```

making it possible to create type-safe fluent interfaces, i.e. instead of:

```java
Customer obj = ...
obj.property("name").eq("John")
```

one can write

```java
method<Customer>(obj -> obj.getName() == "John")
```

in type-safe, refactoring friendly manner. And then the library developer will be able to parse the produced Lambda to the corresponding Expression Tree for analysis.

---

For example, [JPA Criteria API](http://docs.oracle.com/javaee/6/tutorial/doc/gjivm.html) could benefit a lot from using JaQue, e.g.:

```java
//instead of this:
Root<Pet> pet = cq.from(Pet.class);
Join<Pet, Owner> owner = pet.join(Pet_.owners);

//it could be this:
Join<Pet, Owner> owner = pet.join(Pet::getOwners);

//and instead of this:
query.where(pet.get(Pet_.color).isNull());
query.where(builder.equal(pet.get(Pet_.name), "Fido")
	.and(builder.equal(pet.get(Pet_.color), "brown")));
	
//it could be this:
query.where(pet -> pet.getColor() == null);
query.where(pet -> (pet.getName() == "Fido") && (pet.getColor() == "brown"));
```

If you are looking for an opportunity to start an open source project, implementing the above should be very beneficial for a very large developer community. Should you start this or any other open source project based on Expressions, I'll be happy to [assist you](mailto://collinalpert@gmail.com).

#### How to write fluent interface with Expressions?

- Suppose you want to reference some class property

```java
import com.github.collinalpert.expressions.expression.MemberExpression;import com.github.collinalpert.expressions.expression.UnaryExpression;public class Fluent<T> {
	// this interface is required to make the lambda Serializable, which removes a need for 
	// jdk.internal.lambda.dumpProxyClasses system property. See below.
	public static interface Property<T, R> extends Function<T, R>, Serializable {
	}

	public Fluent<T> property(Property<T, ?> propertyRef) {
		LambdaExpression parsed = LambdaExpression.parse(propertyRef);
		Expression body = parsed.getBody();
		
		// remove casts
		while (body instanceof UnaryExpression)
			methodCall = ((UnaryExpression) methodCall).getFirst();

		// checks are omitted for brevity
		Member member = ((MemberExpression) ((InvocationExpression) methodCall)
				.getTarget()).getMember();
		
		// use member
		...
		
		return this;
	}
}
```

- Now your users will be able to write

```java
Fluent<Customer> f = new Fluent<Customer>();
f.property(Customer::getName);
```

> Make the lambda Serializable, as shown in example above. If the lambda is not serializable, the [jdk.internal.lambda.dumpProxyClasses](https://bugs.openjdk.java.net/browse/JDK-8023524) system property must be set and point to an existing writable directory to give the parser access to the lambda byte code.

Install
-------

You can include the Maven dependency:
```xml
<dependency>
    <groupId>com.github.collinalpert</groupId>
    <artifactId>expressions</artifactId>
    <version>2.7.0</version>
</dependency>
```

Or add the [JAR](https://github.com/CollinAlpert/Expressions/releases/latest) to your project.
