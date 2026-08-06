package retrace.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetraceTest {
    @Test
    fun retracesSimpleStackFrame() {
        val mapping =
            """
            com.example.Foo -> a:
            # {'id':'sourceFile','fileName':'Foo.kt'}
                1:1:void bar():42:42 -> b
            """.trimIndent()

        assertEquals(
            listOf("  at com.example.Foo.bar(Foo.kt:42)"),
            Retrace.retraceLines(mapping, listOf("  at a.b(SourceFile:1)")),
        )
    }

    @Test
    fun retracesWithCustomRegex() {
        val mapping =
            """
            com.example.Foo -> a:
            # {'id':'sourceFile','fileName':'Foo.kt'}
                1:1:void bar():42:42 -> b
            """.trimIndent()

        assertEquals(
            "com.example.Foo#bar#Foo.kt:42",
            Retrace.retrace(
                mapping = mapping,
                stackTrace = "a#b#SourceFile:1",
                config = RetraceConfig(regex = "%c#%m#%S"),
            ),
        )
    }

    @Test
    fun verboseIncludesMethodSignature() {
        val mapping =
            """
            com.example.Foo -> a:
            # {'id':'sourceFile','fileName':'Foo.kt'}
                1:1:int bar(java.lang.String):42:42 -> b
            """.trimIndent()

        assertEquals(
            "  at com.example.Foo.int bar(java.lang.String)(Foo.kt:42)",
            Retrace.retrace(
                mapping = mapping,
                stackTrace = "  at a.b(SourceFile:1)",
                config = RetraceConfig(verbose = true),
            ),
        )
    }

    @Test
    fun exportedRetracerIdCanBeDisposed() {
        val mapping =
            """
            com.example.Foo -> a:
            # {'id':'sourceFile','fileName':'Foo.kt'}
                1:1:void bar():42:42 -> b
            """.trimIndent()

        val retracerId = createRetracerExport(mapping, regex = "", verbose = false)

        assertEquals(
            "  at com.example.Foo.bar(Foo.kt:42)",
            retraceWithExport(retracerId, "  at a.b(SourceFile:1)"),
        )
        assertTrue(disposeRetracerExport(retracerId))
        assertFailsWith<IllegalArgumentException> {
            retraceWithExport(retracerId, "  at a.b(SourceFile:1)")
        }
    }

    @Test
    fun exportedRetracerIdsIncreaseSequentially() {
        val firstRetracerId = createRetracerExport(mapping = "", regex = "", verbose = false)
        val secondRetracerId = createRetracerExport(mapping = "", regex = "", verbose = false)

        assertEquals(firstRetracerId + 1, secondRetracerId)
        assertTrue(disposeRetracerExport(firstRetracerId))
        assertTrue(disposeRetracerExport(secondRetracerId))
    }

    @Test
    fun expandsInlineFrames() {
        val mapping =
            """
            com.example.Foo -> a:
            # {'id':'sourceFile','fileName':'Foo.kt'}
                1:1:void inlinee():10:10 -> b
                1:1:void caller():20:20 -> b
            """.trimIndent()

        assertEquals(
            listOf(
                "  at com.example.Foo.inlinee(Foo.kt:10)",
                "  at com.example.Foo.caller(Foo.kt:20)",
            ),
            Retrace.retraceLines(mapping, listOf("  at a.b(SourceFile:1)")),
        )
    }

    @Test
    fun insertsOrForAmbiguousOriginalLines() {
        val mapping =
            """
            com.example.Foo -> a:
                1:1:void f():10:12 -> b
            """.trimIndent()

        assertEquals(
            listOf(
                "  at com.example.Foo.f(Foo.java:10)",
                "  <OR> at com.example.Foo.f(Foo.java:11)",
                "  <OR> at com.example.Foo.f(Foo.java:12)",
            ),
            Retrace.retraceLines(mapping, listOf("  at a.b(SourceFile:1)")),
        )
    }

    @Test
    fun retracesOutlineContextAcrossStack() {
        val mapping =
            """
            # {'id':'com.android.tools.r8.mapping','version':'2.2'}
            package.Class${'$'}${'$'}ExternalSyntheticOutline0 -> package.internal.X:
            # {'id':'sourceFile','fileName':'R8${'$'}${'$'}SyntheticClass'}
            # {'id':'com.android.tools.r8.synthesized'}
                1:2:long package.${'$'}HASH${'$'}0.m(long,long,long):0:1 -> a
                # {'id':'com.android.tools.r8.outline'}
            package.Class -> package.internal.Y:
            # {'id':'sourceFile','fileName':'FieldDefinition.java'}
                1:6:void foo():21:26 -> a
                7:7:void foo():0:0 -> a
                # {'id':'com.android.tools.r8.outlineCallsite','positions':{'1':10,'2':11},'outline':'Lpackage/internal/X;a(JJJ)J'}
                8:9:void foo():38:39 -> a
                10:10:void inlineeInOutline():1337:1337 -> a
                10:10:void foo():42 -> a
                11:11:void foo():44:44 -> a
            """.trimIndent()

        assertEquals(
            listOf(
                "Error in something",
                "  at package.Class.inlineeInOutline(FieldDefinition.java:1337)",
                "  at package.Class.foo(FieldDefinition.java:42)",
            ),
            Retrace.retraceLines(
                mapping,
                listOf(
                    "Error in something",
                    "  at package.internal.X.a(SourceFile:1)",
                    "  at package.internal.Y.a(SourceFile:7)",
                ),
            ),
        )
    }

    @Test
    fun appliesRewriteFrameOnThrownExceptionContext() {
        val mapping =
            """
            # { id: 'com.android.tools.r8.mapping', version: '2.0' }
            java.lang.NullPointerException -> a.b:
            some.Class -> a:
              4:4:void other.Class.inlinee():23:23 -> a
              4:4:void caller(other.Class):7 -> a
              # { id: 'com.android.tools.r8.rewriteFrame', conditions: ['throws(Ljava/lang/NullPointerException;)'], actions: ['removeInnerFrames(1)'] }
            """.trimIndent()

        assertEquals(
            listOf(
                "java.lang.NullPointerException: boom",
                "  at some.Class.caller(Class.java:7)",
            ),
            Retrace.retraceLines(
                mapping,
                listOf(
                    "a.b: boom",
                    "  at a.a(SourceFile:4)",
                ),
            ),
        )
    }
}
