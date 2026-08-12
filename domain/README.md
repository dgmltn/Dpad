# :domain

Pure-Kotlin app-wide domain models and repository/service contracts for Dpad. Depends only on kotlinx-coroutines-core. No dependency on :protocol, :data, or any platform API — standalone, with a one-way dependency edge into it from :data (:domain never depends back). Consumed by feature modules and :data, which implements every :domain contract and wires the bindings together in its Koin `dataModule`.
