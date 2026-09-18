# CI static checks

Run from the `sdd-demo/` directory:

```sh
python3 scripts/ci/validate_workflows.py
python3 scripts/ci/validate_release_secrets.py
python3 -m unittest discover -s scripts/ci -p 'test_*.py'
```

`run_tests.sh` combines those checks with the local Gradle preflight. It does not create, decode, or
upload a release keystore. Release testing must use protected GitHub Secrets or a protected
`release` Environment and a disposable unique `YYYY.MM.DD` date tag.

Never commit `*.jks`, `*.keystore`, `*.p12`, Base64 keystore content, passwords, tokens, or
generated release credentials. The release workflow keeps the decoded keystore under
`$RUNNER_TEMP`, verifies the APK, publishes only public certificate/checksum metadata, and removes
the temporary file with an unconditional cleanup step.
