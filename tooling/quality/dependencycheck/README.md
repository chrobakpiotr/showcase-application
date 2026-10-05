# OWASP Dependency-Check accepted risks

`accepted-risks.xml` contains temporary, explicitly scoped vulnerability
exceptions. Every suppression must identify an owner, a review/expiry date that
matches its `until` date, a package URL, and a vulnerability. Regex package URLs
must be anchored and identify one exact package version.

Run the expiry guard and its standard-library tests with:

```sh
python3 tooling/quality/dependencycheck/check_accepted_risks.py --warn-days 30
python3 -m unittest discover -s tooling/quality/dependencycheck/tests
```

The guard warns on or before the expiry date when it is within 30 days, and
fails starting the day after expiry. A warning does not renew or extend the
exception. Remove the exception only after remediation is verified; never
change its deadline to make CI pass.
