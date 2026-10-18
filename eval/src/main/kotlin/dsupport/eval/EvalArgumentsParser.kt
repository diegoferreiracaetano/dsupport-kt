package dsupport.eval

class EvalArguments(val caseIds: Set<String>, val repeat: Int)

/** Reads the command line: case ids to keep, and `--repeat=N` to run every case N times. */
class EvalArgumentsParser {

    fun parse(args: Array<String>): EvalArguments {
        var repeat = 1
        val ids = mutableSetOf<String>()
        args.forEach { argument ->
            when {
                argument.startsWith("--repeat=") -> repeat = parseRepeat(argument.removePrefix("--repeat="))
                argument.startsWith("--") -> throw IllegalArgumentException("unknown option: $argument")
                else -> ids += argument
            }
        }
        return EvalArguments(ids, repeat)
    }

    private fun parseRepeat(text: String): Int =
        text.toIntOrNull()?.takeIf { it >= 1 } ?: throw IllegalArgumentException("--repeat must be a whole number of at least 1, got '$text'")
}
