package it.sephiroth.android.library.widget;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class ReflectionUtils {

	public static final class ReflectionException extends Exception {

		private static final long serialVersionUID = 1L;

		public ReflectionException( Throwable e ) {
			super( e );
		}

		public ReflectionException( String string ) {
			super( string );
		}
	}


	/**
	 * Try to invoke a method of the passed object. It does not throw any exception if the methodName does not exist on the declared
	 * class object
	 *
	 * @param object
	 * @param methodName
	 * @param parameterTypes  Parameter types of the invoking method
	 * @param parameterValues Parameter values to be passed to the method being invoked
	 * @return
	 * @throws ReflectionException
	 * @throws IllegalArgumentException if parameterTypes and parameterValues have different length
	 */
	@SuppressWarnings("unchecked")
	public static <T> T invokeMethod( final Object object, final String methodName, final Class<?>[] parameterTypes, Object... parameterValues ) throws ReflectionException {
		return (T) helper( object, null, methodName, parameterTypes, parameterValues );
	}

	/**
	 * @param object
	 * @param methodName
	 * @return
	 * @throws ReflectionException
	 */
	@SuppressWarnings("unchecked")
	public static <T> T invokeMethod( Object object, String methodName ) throws ReflectionException {
		return (T) helper( object, null, methodName, null, null );
	}

	@SuppressWarnings("unchecked")
	private static <T> T helper( Object target, final String className, final String methodName, final Class<?>[] argTypes, final Object[] args ) throws ReflectionException {
		try {
			Class<?> cls;
			if( target != null ) {
				cls = target.getClass();
			}
			else {
				cls = Class.forName( className );
				target = cls;
			}

			Method method;

			if( null != argTypes ) {
				method = getMethod( cls, methodName, argTypes );
				return (T) method.invoke( target, args );
			}
			else {
				method = getMethod( cls, methodName );
				return (T) method.invoke( target );
			}

		} catch( final IllegalAccessException e ) {
			throw new ReflectionException( e );
		} catch( final InvocationTargetException e ) {
			throw new ReflectionException( e );
		} catch( final ClassNotFoundException e ) {
			throw new ReflectionException( e );
		} catch( NullPointerException e ) {
			throw new ReflectionException( e );
		}
	}

	public static Method getMethod( Class<?> classObject, String methodName, Class<?>[] paramTypes ) {
		try {
			return classObject.getMethod( methodName, paramTypes );
		} catch( NoSuchMethodException e ) {}
		return null;
	}

	public static Method getMethod( Class<?> classObject, String methodName ) {
		try {
			return classObject.getMethod( methodName );
		} catch( NoSuchMethodException e ) {}
		return null;
	}
}
