package ai.milf.client

import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.session.MilfSessionController
import ai.milf.client.session.androidSessionDependencies
import android.app.Application

class MilfApplication : Application() {
    lateinit var sessionController: MilfSessionController
        private set

    override fun onCreate() {
        super.onCreate()
        sessionController = MilfSessionController(
            dependencies = androidSessionDependencies(this),
            graph = RelationshipGraph.demo()
        )
    }
}
