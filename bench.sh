#!/usr/bin/env bash

declare -A IMAGE_SIZES
IMAGE_SIZES[SIMPLE_VORONOI_512x512_1.png]=512
IMAGE_SIZES[SIMPLE_VORONOI_512x512_2.png]=512
IMAGE_SIZES[SIMPLE_VORONOI_512x512_3.png]=512
IMAGE_SIZES[SIMPLE_VORONOI_512x512_4.png]=512
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_1.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_2.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_3.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_4.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_5.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_6.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_7.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_1024x1024_8.png]=1024
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_1.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_2.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_3.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_4.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_5.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_6.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_7.png]=2048
IMAGE_SIZES[SIMPLE_VORONOI_2048x2048_8.png]=2048
N_IMAGES=20

STRATEGIES=( GRID_SCAN PROGRESSIVE_SCAN GREEDY_RANGE_SCAN )

solve_one() {
    IMAGE="$( echo "${!IMAGE_SIZES[@]}" | cut -d' ' -f $(( ($RANDOM % $N_IMAGES) + 1)) )"

    IMAGE_SIZE="${IMAGE_SIZES[$IMAGE]}"

    VP_RIGHT_X="$(( ($RANDOM % ($IMAGE_SIZE - 6)) + 3 ))"
    VP_LEFT_X="$(( ($RANDOM % ($VP_RIGHT_X - 2)) + 1 ))"
    S_X="$(( ($RANDOM % ($VP_RIGHT_X - $VP_LEFT_X)) + $VP_LEFT_X ))"

    VP_RIGHT_Y="$(( ($RANDOM % ($IMAGE_SIZE - 6)) + 3 ))"
    VP_LEFT_Y="$(( ($RANDOM % ($VP_RIGHT_Y - 2)) + 1 ))"
    S_Y="$(( ($RANDOM % ($VP_RIGHT_Y - $VP_LEFT_Y)) + $VP_LEFT_Y ))"

    STRATEGY="${STRATEGIES[$RANDOM % ${#STRATEGIES[@]}]}"

    curl "http://127.0.0.1:8000/scan?w=$IMAGE_SIZE&h=$IMAGE_SIZE&x0=$VP_LEFT_X&x1=$VP_RIGHT_X&y0=$VP_LEFT_Y&y1=$VP_RIGHT_Y&xS=$S_X&yS=$S_Y&s=$STRATEGY&i=$IMAGE" -o- >/dev/null 2>/dev/null
}

main() {
    export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-amd64
    export PATH="$JAVA_HOME/bin:$PATH"

    ( cd radarscanner-1.0-SNAPSHOT && exec ./bin/radarscanner >/dev/null ) &
    SERVER_PID=$!

    sleep 2 # wait for server to start

    for _ in {1..100}; do
        # during execution the server will print execution information (request info + metrics) to stderr, with a dangling `;`
        # `time` will finish afterwards and print the execution time and a newline character
        /usr/bin/time -f '%e' -- solve_one
    done

    kill -SIGKILL $SERVER_PID
}

main 2>&1