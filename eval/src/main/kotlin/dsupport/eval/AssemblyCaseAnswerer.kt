package dsupport.eval

import dsupport.cli.DSupportRuntime
import dsupport.contract.AgentEvent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.TimeSource

/** Answers a case with the real assistant, giving each case its own empty tickets file so cases never affect each other. */
class AssemblyCaseAnswerer(
    private val runtime: DSupportRuntime,
    private val workDir: Path,
    private val builder: CaseRunBuilder = CaseRunBuilder(),
) : CaseAnswerer {

    override suspend fun answer(case: GoldenCase): CaseRun {
        val ticketsFile = Files.createTempFile(workDir, "tickets-${case.id}-", ".json")
        Files.writeString(ticketsFile, "[]")
        val answerCustomer = runtime.assemble(ticketsFile)
        val events = mutableListOf<AgentEvent>()
        val mark = TimeSource.Monotonic.markNow()
        answerCustomer(case.question).collect { events += it }
        val elapsedMs = mark.elapsedNow().inWholeMilliseconds
        return builder.build(events, Files.readString(ticketsFile), elapsedMs)
    }
}
