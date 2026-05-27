package org.seqra.dataflow.cir.ap.ifds.analysis

import org.seqra.dataflow.ap.ifds.AccessPathBase
import org.seqra.dataflow.ap.ifds.ElementAccessor
import org.seqra.dataflow.ap.ifds.ReferenceAccessor
import org.seqra.dataflow.cir.ap.ifds.taint.PositionAccess
import org.seqra.dataflow.configuration.core.Argument
import org.seqra.dataflow.configuration.core.ContainsMark
import org.seqra.dataflow.configuration.core.TaintMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CIRPreconditionMirrorTest {

    @Test
    fun `cirMirrorPreconditionPositionAccessCandidates BaseOnly Argument has three variants`() {
        val cm = ContainsMark(Argument(0), TaintMark("use-after-free"))
        val candidates = cm.cirMirrorPreconditionPositionAccessCandidates()
        assertEquals(3, candidates.size)

        val simple = candidates.filterIsInstance<PositionAccess.Simple>().singleOrNull()
        assertTrue(simple != null && simple.base == AccessPathBase.Argument(0))

        val underElem = candidates.filterIsInstance<PositionAccess.Complex>().single {
            it.accessor is ElementAccessor &&
                it.base is PositionAccess.Simple &&
                (it.base as PositionAccess.Simple).base == AccessPathBase.Argument(0)
        }
        assertIs<ElementAccessor>(underElem.accessor)

        val underRef = candidates.filterIsInstance<PositionAccess.Complex>().single {
            it.accessor is ReferenceAccessor &&
                it.base is PositionAccess.Simple &&
                (it.base as PositionAccess.Simple).base == AccessPathBase.Argument(0)
        }
        assertIs<ReferenceAccessor>(underRef.accessor)
    }
}
