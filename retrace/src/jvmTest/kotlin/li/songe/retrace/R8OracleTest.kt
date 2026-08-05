package li.songe.retrace

import com.android.tools.r8.retrace.ProguardMapProducer
import com.android.tools.r8.retrace.ProguardMappingSupplier
import com.android.tools.r8.retrace.RetraceOptions
import com.android.tools.r8.retrace.RetraceStackTraceContext
import com.android.tools.r8.retrace.StringRetrace
import kotlin.test.Test
import kotlin.test.assertEquals

class R8OracleTest {
    @Test
    fun matchesR8ForSimpleAndInlineFrames() {
        val mapping =
            """
            com.example.Foo -> a:
            # {'id':'sourceFile','fileName':'Foo.kt'}
                1:1:void inlinee():10:10 -> b
                1:1:void caller():20:20 -> b
            """.trimIndent()
        val stackTrace = listOf("  at a.b(SourceFile:1)")

        assertEquals(r8Retrace(mapping, stackTrace), Retrace.retraceLines(mapping, stackTrace))
    }

    @Test
    fun matchesR8ForOutlineStringRetraceAllFrames() {
        val mapping =
            $$$"""
            # {'id':'com.android.tools.r8.mapping','version':'2.2'}
            package.Class$$ExternalSyntheticOutline0 -> package.internal.X:
            # {'id':'sourceFile','fileName':'R8$$SyntheticClass'}
            # {'id':'com.android.tools.r8.synthesized'}
                1:2:long package.$HASH$0.m(long,long,long):0:1 -> a
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
        val stackTrace =
            listOf(
                "Error in something",
                "  at package.internal.X.a(SourceFile:1)",
                "  at package.internal.Y.a(SourceFile:7)",
            )

        assertEquals(r8Retrace(mapping, stackTrace), Retrace.retraceLines(mapping, stackTrace))
    }

    private fun r8Retrace(mapping: String, stackTrace: List<String>): List<String> {
        val mappingSupplier =
            ProguardMappingSupplier.builder()
                .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
                .build()
        val retracer =
            StringRetrace.create(
                RetraceOptions.builder()
                    .setMappingSupplier(mappingSupplier)
                    .build(),
            )
        return retracer.retrace(stackTrace, RetraceStackTraceContext.empty()).result
    }
}
