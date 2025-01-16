#!/usr/bin/env sh

set -e

# Check that we are in the correct folder
case "$PWD" in
  *dbeaver/tools)
    ;;
  *)
    echo "Error: Script must be run from the 'dbeaver/tools' directory"
    exit 1
    ;;
esac

root_dir=$(realpath ../..)

clone_if_not_exist () {
    repo_name=$1
    echo "Ensuring $repo_name is cloned"
    repo_local_path="$root_dir/$repo_name"
    if [ ! -d "$repo_local_path" ]; then
        git clone "https://github.com/dbeaver/$repo_name.git" "$repo_local_path"
    fi
}

ensure_dependencies_cloned () {
    repo_name=$1
    echo "Ensuring dependencies for $repo_name are cloned"
    while IFS= read -r line; do
        clone_if_not_exist "$line"
    done < "$root_dir/$repo_name/project.deps"
}

clone_with_dependencies () {
    repo_name=$1
    echo "Ensuring $repo_name is cloned along with dependencies"
    clone_if_not_exist "$repo_name"
    ensure_dependencies_cloned "$repo_name"
}

ensure_dependencies_cloned "dbeaver"
clone_with_dependencies "idea-rcp-launch-config-generator"

maven="$root_dir/dbeaver-common/root/mvnw"

echo "Triggering code generation"
$maven clean compile -T 1C -f "$root_dir/dbeaver/product/aggregate"

echo "Running idea launch config generator"
"$root_dir/idea-rcp-launch-config-generator/runGenerator.sh" -f "$root_dir/dbeaver"
