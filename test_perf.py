import time
import os

def run_tests():
    os.system("./gradlew testDebugUnitTest --tests '*DashboardNewsRepositoryTest*' || true")

start_time = time.time()
run_tests()
print(f"Time taken: {time.time() - start_time} seconds")
