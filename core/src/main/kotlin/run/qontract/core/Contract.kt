package run.qontract.core

import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.contractFilePath
import run.qontract.core.utilities.readFile
import run.qontract.stub.HttpStub
import run.qontract.test.HttpClient

data class Contract(val contractGherkin: String, val majorVersion: Int = 0, val minorVersion: Int = 0) {
    fun startFake(port: Int) = HttpStub(contractGherkin, emptyList(), "localhost", port)

    fun test(endPoint: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val results = contractBehaviour.executeTests(HttpClient(endPoint))
        if (results.hasFailures())
            throw ContractException(results.report())
    }

    fun test(fake: HttpStub) = test(fake.endPoint)

    fun samples(fake: HttpStub) = samples(fake.endPoint)
    fun samples(endPoint: String) {
        val contractBehaviour = ContractBehaviour(contractGherkin)
        val httpClient = HttpClient(endPoint)

        contractBehaviour.generateTestScenarios(emptyList()).fold(Results()) { results, scenario ->
            scenario.kafkaMessagePattern?.let { kafkaMessagePattern ->
                val message = """KAFKA MESSAGE
${kafkaMessagePattern.generate(scenario.resolver).toDisplayableString()}""".trimMargin().prependIndent("| ")
                println(message)
                Results(results = results.results.plus(Triple(Result.Success(), null, null)).toMutableList())
            } ?: Results(results = results.results.plus(executeTest(scenario, httpClient)).toMutableList())
        }
    }

    companion object {
        @JvmStatic
        fun fromGherkin(contractGherkin: String, majorVersion: Int, minorVersion: Int): Contract {
            return Contract(contractGherkin, majorVersion, minorVersion)
        }

        @JvmStatic
        fun behaviourFromFile(contractFilePath: String) = ContractBehaviour(readFile(contractFilePath))

        @JvmStatic
        fun behaviourFromServiceContractFile() = behaviourFromFile(contractFilePath)
    }
}