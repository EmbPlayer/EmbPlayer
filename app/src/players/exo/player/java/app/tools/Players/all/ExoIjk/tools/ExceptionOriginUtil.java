/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright 2026-present Emre Hyuseinov (plaxir) <plaxirstudio@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.tools.Players.all.ExoIjk.tools;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class ExceptionOriginUtil {

    private ExceptionOriginUtil() {}

    /**
     * Check whether throwable or any nested cause/suppressed contains a stack frame
     * matching the given className and methodName, or whether any cause is an instance
     * of expectedType (if non-null).
     *
     * @param throwable the exception to inspect
     * @param className full class name to match (exact) or package prefix if usePrefixMatch true; may be null
     * @param methodName method name to match (exact). If null, only className is matched.
     * @param expectedType optional exception class to look for in the cause chain (can be null)
     * @param usePrefixMatch if true treat className as a package/class prefix
     * @return true if a match is found
     */
    public static boolean originatesFrom(Throwable throwable,
                                         String className,
                                         String methodName,
                                         Class<? extends Throwable> expectedType,
                                         boolean usePrefixMatch) {
        if (throwable == null) return false;
        Set<Throwable> seen = new HashSet<>();
        return originatesFromRecursive(throwable, className, methodName, expectedType, usePrefixMatch, seen);
    }

    private static boolean originatesFromRecursive(Throwable t,
                                                   String className,
                                                   String methodName,
                                                   Class<? extends Throwable> expectedType,
                                                   boolean usePrefixMatch,
                                                   Set<Throwable> seen) {
        if (t == null || seen.contains(t)) return false;
        seen.add(t);

        if (expectedType != null && expectedType.isInstance(t)) {
            return true;
        }

        StackTraceElement[] frames = t.getStackTrace();
        if (frames != null && className != null) {
            for (StackTraceElement el : frames) {
                String frameClass = el.getClassName();
                String frameMethod = el.getMethodName();

                boolean classMatches = usePrefixMatch
                        ? frameClass != null && frameClass.startsWith(className)
                        : frameClass != null && frameClass.equals(className);

                boolean methodMatches = (methodName == null) || methodName.equals(frameMethod);

                if (classMatches && methodMatches) {
                    return true;
                }
            }
        }

        for (Throwable sup : t.getSuppressed()) {
            if (originatesFromRecursive(sup, className, methodName, expectedType, usePrefixMatch, seen)) {
                return true;
            }
        }

        return originatesFromRecursive(t.getCause(), className, methodName, expectedType, usePrefixMatch, seen);
    }

    /**
     * Find the first StackTraceElement that matches the given criteria anywhere in the
     * throwable, its causes, or its suppressed exceptions.
     *
     * @param throwable the exception to inspect
     * @param classOrPrefix full class name to match exactly or package/class prefix if usePrefix true; may be null
     * @param methodName method name to match; if null only class/prefix is matched
     * @param expectedType optional exception class to match anywhere in the chain; if non-null,
     *                     a match of the type will return Optional.empty() unless a frame also matches
     * @param usePrefix if true treat classOrPrefix as a prefix
     * @return Optional of the matching StackTraceElement; empty if not found
     */
    public static Optional<StackTraceElement> findOrigin(Throwable throwable,
                                                         String classOrPrefix,
                                                         String methodName,
                                                         Class<? extends Throwable> expectedType,
                                                         boolean usePrefix) {
        if (throwable == null) return Optional.empty();
        Set<Throwable> seen = new HashSet<>();
        return findOriginRecursive(throwable, classOrPrefix, methodName, expectedType, usePrefix, seen);
    }

    private static Optional<StackTraceElement> findOriginRecursive(Throwable t,
                                                                   String classOrPrefix,
                                                                   String methodName,
                                                                   Class<? extends Throwable> expectedType,
                                                                   boolean usePrefix,
                                                                   Set<Throwable> seen) {
        if (t == null || seen.contains(t)) return Optional.empty();
        seen.add(t);

        boolean typeMatches = expectedType != null && expectedType.isInstance(t);

        StackTraceElement[] frames = t.getStackTrace();
        if (frames != null && classOrPrefix != null) {
            for (StackTraceElement el : frames) {
                String frameClass = el.getClassName();
                String frameMethod = el.getMethodName();

                boolean classMatches = usePrefix
                        ? frameClass != null && frameClass.startsWith(classOrPrefix)
                        : frameClass != null && frameClass.equals(classOrPrefix);

                boolean methodMatches = (methodName == null) || methodName.equals(frameMethod);

                if (classMatches && methodMatches) {
                    return Optional.of(el);
                }
            }
        }

        for (Throwable sup : t.getSuppressed()) {
            Optional<StackTraceElement> found = findOriginRecursive(sup, classOrPrefix, methodName, expectedType, usePrefix, seen);
            if (found.isPresent()) return found;
        }

        Optional<StackTraceElement> fromCause = findOriginRecursive(t.getCause(), classOrPrefix, methodName, expectedType, usePrefix, seen);
        if (fromCause.isPresent()) return fromCause;

        if (typeMatches) return Optional.empty();

        return Optional.empty();
    }

    /**
     * Check whether any throwable in the chain is an instance of expectedType.
     *
     * @param throwable the exception to inspect
     * @param expectedType the exception class to look for
     * @return true if found anywhere in the chain or suppressed exceptions
     */
    public static boolean causedBy(Throwable throwable, Class<? extends Throwable> expectedType) {
        if (throwable == null || expectedType == null) return false;
        Set<Throwable> seen = new HashSet<>();
        Throwable t = throwable;
        while (t != null && !seen.contains(t)) {
            if (expectedType.isInstance(t)) return true;
            seen.add(t);
            for (Throwable sup : t.getSuppressed()) {
                if (expectedType.isInstance(sup)) return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
