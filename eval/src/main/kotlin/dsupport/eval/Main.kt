package dsupport.eval

import dsupport.cli.DSupportRuntime
import dsupport.cli.SettingsReader
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private const val REQUEST_TIMEOUT_MS = 120_000L

private val REPORT_STAMP = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

/** "Late" depends on today, so the evaluation pins the date: its results must not change with the calendar. */
private val EVAL_REFERENCE_DATE = LocalDate(2026, 10, 15)

suspend fun main(args: Array<String>) {
    val settings = try {
        SettingsReader().read(System.getenv(), referenceDate = EVAL_REFERENCE_DATE)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    val arguments = try {
        EvalArgumentsParser().parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("${e.message}\nUsage: ./gradlew :eval:run --args=\"[case ids] [--repeat=N]\"")
        exitProcess(1)
    }
    val cases = GoldenDatasetLoader().load(settings.dataDir.resolve("golden.json"))
    val planned = cases.count { arguments.caseIds.isEmpty() || it.id in arguments.caseIds } * arguments.repeat
    println("Planned: ${CostEstimate().describe(planned, settings.modelId)}")
    val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
    try {
        val progress = ProgressLine()
        val runner = EvalRunner(
            AssemblyCaseAnswerer(DSupportRuntime(settings, http), Files.createTempDirectory("dsupport-eval")),
        ) { case, result -> println(progress.format(case, result)) }

        val results = runner.run(cases, only = arguments.caseIds, repeat = arguments.repeat)

        val now = java.time.LocalDateTime.now()
        val markdown = EvalReport(results).toMarkdown(
            "Evaluation ${now.toLocalDate()} (${settings.modelId}, reference date $EVAL_REFERENCE_DATE, ${arguments.repeat} run(s) per case)",
        )
        val report = Path.of("reports", "eval-${now.format(REPORT_STAMP)}.md")
        Files.createDirectories(report.parent)
        Files.writeString(report, markdown)
        println("\n$markdown")
        println("Report written to $report")
    } finally {
        http.close()
    }
}
