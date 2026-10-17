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

/** "Late" depends on today, so the evaluation pins the date: its results must not change with the calendar. */
private val EVAL_REFERENCE_DATE = LocalDate(2026, 10, 15)

suspend fun main(args: Array<String>) {
    val settings = try {
        SettingsReader().read(System.getenv(), referenceDate = EVAL_REFERENCE_DATE)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(1)
    }
    val cases = GoldenDatasetLoader().load(settings.dataDir.resolve("golden.json"))
    val http = HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MS } }
    try {
        val progress = ProgressLine()
        val runner = EvalRunner(
            AssemblyCaseAnswerer(DSupportRuntime(settings, http), Files.createTempDirectory("dsupport-eval")),
        ) { case, result -> println(progress.format(case, result)) }

        val results = runner.run(cases, only = args.toSet())

        val today = java.time.LocalDate.now()
        val markdown = EvalReport(results).toMarkdown("Evaluation $today (${settings.modelId}, reference date $EVAL_REFERENCE_DATE)")
        val report = Path.of("reports", "eval-$today.md")
        Files.createDirectories(report.parent)
        Files.writeString(report, markdown)
        println("\n$markdown")
        println("Report written to $report")
    } finally {
        http.close()
    }
}
