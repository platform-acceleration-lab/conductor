# Netflix Conductor Fork

Forked from [Netflix Conductor](https://github.com/Netflix/conductor)

## Branches

- `groundskeeper` - used for PAL `groundskeeper-server`,
  in the `pal-caddy` github repo.
- `master` - mirrors upstream's `master` 
- `portal` - used for Enablement team's `pal-portal` github repo

## To update from the upstream (Netflix) repo

For example, to update the `groundskeeper` branch
```
# You'll only need to do this once after cloning
git remote add upstream https://github.com/Netflix/conductor.git

# Refresh upstream repo in local repo
git fetch upstream

# Update local master
git checkout -b master --track origin/master
git merge upstream/master

# Update local groundskeeper
git checkout groundskeeper
git rebase upstream/<TAG>
```
... where `<TAG>` is the version you want to upgrade to, e.g. `v2.25.5`

... Then, fix merge conflicts.

You may want to change the branch that `groundkskeeper-conductor`
git repo uses to deploy to review in order to test the changes in a temporary
branch before changing the `groundskeeper` branch.

When done, push master and groundskeeper branches.

## Fork status

- Most recent Netflix release is v2.25.5 (Feb 15, 2020)
- As of Mar 6 2020, `groundskeeper` branch is tracking release v2.25.5

Here is a rundown of the most important of our additions and changes
which are not (yet) in the upstream repo:

1. Rolls over task_log indices yearly rather than weekly 

   This is to accommodate a smaller Elasticsearch tile instance
   (each task_log index counts against the number of indexes
   quota in the Elasticsearch plan).
  
1. Handle re-indexing either Redis or MySQL at startup

   This is only necessary if using in-memory Elasticsearch, which we do
   for local development, but is not required unless you need to persist
   state across restarts.
  
1. Use Elasticsearch 6 instead of 5

   This is required because our Elasticsearch provider only supports HTTP
   connections (as opposed to Elasticsearch's "native" TCP protocol),
   which requires ES6.
   "Our" changes are mostly just following the instructions
   on the Conductor site on how to change to ES6.
  
1. Support basic authentication over ES6 REST client

   We do this in order to be able to do basic auth to Elasticsearch,
   which is easier than other alternatives.

1. Replace a malformed migration
   
   There is a migration which uses the ADD COLUMN IF NOT EXISTS syntax,
   which is not supported by the MySQL we are using.
   So we removed the bad migration and created a new migration
   (same version number, different filename) which is compatible with
   all MySQL and MariaDB versions.
